package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableList;
import uk.ac.bris.cs.scotlandyard.model.*;
import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import uk.ac.bris.cs.scotlandyard.model.Board.TicketBoard;
import uk.ac.bris.cs.scotlandyard.model.Move.*;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Ticket;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Transport;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class MiniMaxAI implements Ai {

    //Tree to hold future boards
    Tree gameTree;
    //Keeps track of where Mr X might be
    //Updated after a Mr X turn, so is static for detectives to make use of
    static ImmutableList<Integer> possibleMrXLocations;
    //Holds moves which are pending
    //Used for when detective moves are performed
    LinkedList<Move> moveQueue;
    //Mr X's actual location - will always be -1 in a detective AI
    int mrXLocation;

    //Visitors to extract information from moves
    //Static, public and final so can be accessed by other classes easily (without being modified)
    public final static Visitor<Integer> destinationVisitor = new FunctionalVisitor<>((x -> x.destination),(x -> x.destination2));
    public final static  Visitor<Boolean> moveTypeVisitor = new FunctionalVisitor<>(x -> true, x -> false);
    public final static  Visitor<Boolean> secretMoveVisitor = new FunctionalVisitor<>(x -> x.ticket.equals(Ticket.SECRET),
            x -> x.ticket1.equals(Ticket.SECRET) || x.ticket2.equals(Ticket.SECRET));

    //A cap on the number of possible Mr X positions to track
    final int POSSIBLE_POSITION_MAX = 7;
    //Number of levels in the tree
    final int TREE_DEPTH = 2;
    //Minimum number of distinct single move destinations needed to exclude double moves
    final int FREEDOM_OF_MOVEMENT = 4;
    //The amount of time the move selection is allowed to run for
    final double TIME_LIMIT = 29;

    //Holds shortest path arrays for all nodes in graph
    public static List<int[]> dijkstraDistances;

    /**
     * Initialises some attributes of the Ai, and gives default values to others
     */
    public void onStart(){
        if(dijkstraDistances == null){
            try{
                dijkstraDistances = Dijkstra.runDijkstraOnGraph(ScotlandYard.standardGraph());
            }
            catch(IOException e){
                dijkstraDistances = null;
            }
        }
        moveQueue = new LinkedList<>();
        gameTree = null;
        possibleMrXLocations = ImmutableList.<Integer>builder().build();
        mrXLocation = -1;
    }

    /**
     * @param moves the move lists to permute
     * @return all of the permutations of moves
     */
    List<List<Move>> permutations(Map<Piece, List<Move>> moves){
        List<List<Move>> playerMoves = new ArrayList<>();
        for(var e : moves.entrySet()){
            playerMoves.add(e.getValue());
        }
        return permuteMoves(playerMoves, 0);
    }

    /**
     * @param playerMoves the lists to combine
     * @param depth counter to determine when to reach the base case
     * @return the permutations of the lists from [depth, playerMoves.size() - 1]
     */
    List<List<Move>> permuteMoves(List<List<Move>> playerMoves, int depth){
        List<List<Move>> permutations = new ArrayList<>();
        if(depth == playerMoves.size() - 1){
            for(Move m : playerMoves.get(depth))
                permutations.add(new ArrayList<>(List.of(m)));
            return permutations;
        }
        else{
            permutations = permuteMoves(playerMoves, depth + 1);
            List<List<Move>> newPermutations = new ArrayList<>();
            for(Move m : playerMoves.get(depth)){
                for(List<Move> ms : permutations){
                    List<Move> newMoveList = new ArrayList<>(ms);
                    newMoveList.add(m);
                    newPermutations.add(newMoveList);
                }
            }
            return newPermutations;
        }
    }

    /**
     * @param board the board containing the available moves
     * @return a map where map.get(p) for some piece p returns a list of all moves p can make
     *         the pieces included are simply all of the pieces who have moves in board.getAvailableMoves()
     */
    Map<Piece, List<Move>> makeMoveMap(Board board){
        Map<Piece, List<Move>> moveMap = new HashMap<>();
        for(Move m : board.getAvailableMoves()){
            Piece commencedBy = m.commencedBy();
            if(moveMap.containsKey(commencedBy))
                moveMap.get(commencedBy).add(m);
            else{
                List<Move> newList = new ArrayList<>();
                newList.add(m);
                moveMap.put(commencedBy, newList);
            }
        }
        return moveMap;
    }

    /**
     * @param move the move to be costed
     * @return a sum of the costs of the tickets used in move
     */
    public static int ticketPrice(Move move){
        int sum = 0;
        for(Ticket t : move.tickets()){
            switch(t){
                case TAXI:
                    sum += 1;
                    break;
                case BUS:
                    sum += 2;
                    break;
                case UNDERGROUND:
                    sum += 3;
                    break;
                case SECRET:
                case DOUBLE:
                    sum += 5;
                    break;
            }
        }
        return sum;
    }

    /**
     * @param moves the moves to be costed
     * @return a sum of all of the ticket costs used in all of the moves
     */
    public static int ticketPrice(List<Move> moves){
        int sum = 0;
        if(moves == null)
            return sum;
        for(Move m : moves){
            sum += ticketPrice(m);
        }
        return sum;
    }

    /**
     * @param moves the list of moves Mr X can make
     * @return a new list, where all 'expensive' moves have been removed
     *         a move is considered expensive if another move exists which can reach the same destination for a cheaper price
     *         secret moves are not considered, as it is decided whether to use those during scoring
     */
    List<Move> cullExpensiveMrXMoves(List<Move> moves){
        //Create a single-length map to make use of cullExpensiveMoves
        var map = cullExpensiveMoves(Map.of(Piece.MrX.MRX, moves));
        return map.get(Piece.MrX.MRX);
    }

    /**
     * @param moveMap the map of moves the players can take
     * @return a new list, where all 'expensive' moves have been removed
     *         a move is considered expensive if another move exists which can reach the same destination for a cheaper price
     *         secret moves are not considered, as it is decided whether to use those during scoring
     */
    Map<Piece, List<Move>> cullExpensiveMoves(Map<Piece, List<Move>> moveMap){
        Map<Piece, List<Move>> newMap = new HashMap<>();
        for(var entry : moveMap.entrySet()){
            var moves = entry.getValue();
            List<Move> newMoves = new ArrayList<>();
            for(Move m : moves){
                boolean addMove = true;
                for(Move n : moves){
                    if(m != n){
                        //If the destinations are the same
                        if(m.visit(destinationVisitor) == n.visit(destinationVisitor)){
                            //If the score of m is worse than n, and is not a secret move
                            if(ticketPrice(m) > ticketPrice(n)){
                                if(!m.visit(secretMoveVisitor))
                                    addMove = false;
                                else{
                                    //If both are secret moves with the same destination,
                                    //and m's score is worse than n's, don't add m.
                                    if(m.visit(secretMoveVisitor) && n.visit(secretMoveVisitor))
                                        addMove = false;
                                }
                            }
                        }
                    }
                }
                if(addMove)
                    newMoves.add(m);
            }
            newMap.put(entry.getKey(), newMoves);
        }
        return newMap;
    }

    /**
     * @param board new gamestate
     * @param currentLocations MrX's possible locations from previous gamestate
     * @param m move used to reach gamestate
     * @return
     */
    private ImmutableList<Integer> getPossibleMrXLocations(Board board, List<Integer> currentLocations, Move m){
        int roundNumber = board.getMrXTravelLog().size();
        //If there have been no reveal rounds up to this point, no information to go off
        if(board.getMrXTravelLog().stream().allMatch(x -> x.location().isEmpty()))
            return ImmutableList.<Integer>builder().build();
        //If a reveal round, return only the current location
        if(board.getSetup().rounds.get(roundNumber - 1)){
            return ImmutableList.of(m.visit(destinationVisitor));
        }
        List<Integer> possibleLocations;
        //For double moves
        if(!m.visit(moveTypeVisitor)){
            //If skipping over a reveal, get the new location and then take the neighbours of it
            if(board.getMrXTravelLog().get(roundNumber - 2).location().isPresent()){
                currentLocations = List.of(board.getMrXTravelLog().get(roundNumber - 2).location().get());
                possibleLocations = getNeighbours(board, currentLocations, ((DoubleMove)m).ticket2);
            }
            //If not, get the neighbours of the neighbours of all current possible locations, and add them.
            else{
                possibleLocations = getNeighbours(board, currentLocations, ((DoubleMove)m).ticket1);
                possibleLocations = getNeighbours(board, possibleLocations, ((DoubleMove)m).ticket2);
            }
        }
        //If a single move, simply get all neighbours of the current possible locations
        else{
            possibleLocations = getNeighbours(board, currentLocations, ((SingleMove)m).ticket);
        }
        //Limits the size of possible locations
        //Grows exponentially, so difficult to maintain an accurate guess of where Mr X could be
        if(possibleLocations.size() > POSSIBLE_POSITION_MAX)
            possibleLocations = possibleLocations.subList(0, POSSIBLE_POSITION_MAX);
        return ImmutableList.copyOf(possibleLocations);
    }

    /**
     * @param board the next gamestate
     * @param currentLocations the current possible locations of Mr X
     * @param t the ticket used by Mr X
     * @return a list of locations that Mr X could reach from any of the previous possible locations
     */
    private List<Integer> getNeighbours(Board board, List<Integer> currentLocations, Ticket t) {
        List<Integer> neighbours = new ArrayList<>();
        var graph = board.getSetup().graph;
        TicketBoard tb = board.getPlayerTickets(Piece.MrX.MRX).get();
        for(int u : currentLocations){
            for(int v : graph.adjacentNodes(u)){
                boolean canReachV = false;
                for(Piece p : board.getPlayers()){
                    if(p.isDetective()){
                        Piece.Detective d = (Piece.Detective)p;
                        if(v == board.getDetectiveLocation(d).get())
                            canReachV = false;
                    }
                }
                Set<Transport> edge = graph.edgeValue(u, v).get();
                for(var transport : edge){
                    Ticket requiredTicket = transport.requiredTicket();
                    if(requiredTicket.equals(t) && tb.getCount(t) > 0){
                        canReachV = true;
                        break;
                    }
                }
                if(t.equals(Ticket.SECRET))
                    canReachV = true;
                if(canReachV)
                    neighbours.add(v);
            }
        }
        return neighbours;
    }

    /**
     * @param moves the list of moves Mr X can make
     * @return the list of moves Mr X can make using only one ticket
     */
    private List<Move> getFreedom(List<Move> moves){
        List<Move> newMoves = new ArrayList<>();
        for(Move m : moves){
            if(m.visit(moveTypeVisitor)){
                newMoves.add(m);
            }
        }
        return newMoves;
    }

    /**
     * @param board the current gamestate
     * @param incidentMoves the move used to reach the current gamestate
     * @param player indicates who is making the move - if true, Mr X. if false, Detectives
     * @return a single-node tree with a decided score
     */
    private Tree evaluateLeaf(Board board, List<Move> incidentMoves, boolean player, ImmutableList<Integer> possibleMrXLocations, Clock clock){
        Tree newTree = new MyTree(board);
        if(!board.getWinner().isEmpty()){
            if(board.getWinner().asList().stream().allMatch(Piece::isMrX))
                newTree.root().setScore(Integer.MAX_VALUE);
            else
                newTree.root().setScore(Integer.MIN_VALUE);
        }
        else{
            newTree.root().setScore(incidentMoves, board, player, possibleMrXLocations, mrXLocation);
        }
        clock.updateClock();
        return newTree;
    }

    /**
     * @param board the current gamestate
     * @param player if true, Mr X is making his move, if false, the detectives are
     * @return the list of moves Mr X it would be sensible for Mr X to take
     */
    private List<Move> getMrXMoves(Board board, boolean player, List<Integer> possibleMrXLocations){
        List<Move> mrXMoves = new ArrayList<>();
        if(player){
            mrXMoves = cullExpensiveMrXMoves(board.getAvailableMoves().asList());
            List<Move> singleMoves = getFreedom(mrXMoves);
            //Get number of unique destinations Mr X can reach with only single moves
            int singleMoveDestinations = (int)singleMoves.stream().map(x -> x.visit(destinationVisitor))
                    .distinct().count();
            //If that number is high enough, remove all double moves (tickets need to be conserved)
            if(singleMoveDestinations >= FREEDOM_OF_MOVEMENT)
                mrXMoves = singleMoves;
        }
        else{
            Board.TicketBoard tb = board.getPlayerTickets(Piece.MrX.MRX).get();
            if(!possibleMrXLocations.isEmpty()){
                for(int u : possibleMrXLocations){
                    var graph = board.getSetup().graph;
                    //Adjacent nodes are single move destinations
                    for(int v : graph.adjacentNodes(u)){
                        //Each ticket used is a different way to reach v from u
                        List<Ticket> ticketsUsed = graph.edgeValue(u, v).get().asList()
                                .stream().map(ScotlandYard.Transport::requiredTicket).collect(Collectors.toList());
                        //Examine each way to move
                        for(Ticket t : ticketsUsed){
                            if(tb.getCount(t) > 0){
                                Move movePerformed = new SingleMove(Piece.MrX.MRX, u, t, v);
                                mrXMoves.add(movePerformed);
                                //If double moves are possible, do something similar again
                                if(tb.getCount(Ticket.DOUBLE) > 0){
                                    //Advance to get updated TicketBoard
                                    Board advanced = ((BoardToGameState)board).forceAdvance(movePerformed);
                                    Board.TicketBoard doubleTicketBoard = advanced.getPlayerTickets(Piece.MrX.MRX).get();
                                    //Each neighbour of w can be reached from u in a double move
                                    for(int w : graph.adjacentNodes(v)){
                                        List<Ticket> ticketsUsedInDoubleMove = graph.edgeValue(v, w).get().asList()
                                                .stream().map(ScotlandYard.Transport::requiredTicket).collect(Collectors.toList());
                                        for(Ticket r : ticketsUsedInDoubleMove){
                                            if(doubleTicketBoard.getCount(r) > 0){
                                                Move doubleMovePerformed = new DoubleMove(Piece.MrX.MRX, u, t, v, r, w);
                                                mrXMoves.add(doubleMovePerformed);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return mrXMoves;
    }

    /**
     * @param board the current gamestate
     * @return the list of moves it would be sensible for the detectives to take
     */
    private List<List<Move>> getDetectiveMoves(Board board){
        Map<Piece, List<Move>> moveMap = cullExpensiveMoves(makeMoveMap(board));
        List<List<Move>> moveLists = permutations(moveMap);
        //Removes all move permutations where two or more detectives share the same destination
        moveLists.removeIf(
                x -> x.stream().map(y -> y.visit(destinationVisitor)).distinct().count() != x.size()
        );
        return moveLists;
    }

    /**
     * @param board the current gamestate
     * @param incidentMoves the move used to reach the current gamestate
     * @param alpha keeps track of the score of the best move the maximiser can take
     * @param beta keeps track of the score of the best move the minimiser can take
     * @param depth how many levels further to go down tree
     * @param maximising whose turn it is at that level in the gametree
     * @param player who is making the move
     * @param clock object to track how long execution is taking
     * @return a gametree where tree.root() is the current board, and its score corresponds to the score of its child
     *         with the best move to take
     */
    private Tree buildTree(Board board, List<Move> incidentMoves, ImmutableList<Integer> possibleMrXLocations,
                           int alpha, int beta, int depth,
                           boolean maximising, boolean player, Clock clock){
        Tree newTree = new MyTree(board);
        if(depth == 0 || !board.getWinner().isEmpty())
            return evaluateLeaf(board, incidentMoves, player, possibleMrXLocations, clock);
        if(maximising){
            int maxEval = Integer.MIN_VALUE;
            List<Move> mrXMoves = getMrXMoves(board, player, possibleMrXLocations);
            if(mrXMoves.isEmpty())
                return evaluateLeaf(board, incidentMoves, player, possibleMrXLocations, clock);
            for(Move m : mrXMoves){
                Board advanced;
                if(player) advanced = ((BoardToGameState)board).advance(m);
                else advanced = ((BoardToGameState)board).forceAdvance(m);
                ImmutableList<Integer> newPossibleMrXLocations = getPossibleMrXLocations(advanced, possibleMrXLocations, m);
                Tree branch = buildTree(advanced, List.of(m), newPossibleMrXLocations,
                        alpha, beta,depth - 1, false, player, clock);
                int eval = branch.root().score();
                maxEval = Integer.max(maxEval, eval);
                alpha = Integer.max(alpha, eval);
                newTree.addTree(branch, m);
                if(alpha >= beta) break;
                if(clock.checkLimit()) break;
            }
            newTree.root().setScore(maxEval);
        }
        else{
            int minEval = Integer.MAX_VALUE;
            List<List<Move>> detectiveMoves = getDetectiveMoves(board);
            for(List<Move> moves : detectiveMoves){
                boolean isMoveValid = true;
                GameState advanced = (GameState)board;
                for(Move m : moves){
                    try { advanced = advanced.advance(m); }
                    catch (IllegalArgumentException e){ isMoveValid = false; }
                }
                if(isMoveValid) {
                    Tree branch = buildTree(advanced, moves, possibleMrXLocations,
                            alpha, beta, depth - 1, true, player, clock);
                    int eval = branch.root().score();
                    minEval = Integer.min(minEval, eval);
                    beta = Integer.min(beta, eval);
                    newTree.addTree(branch, moves);
                    if (alpha >= beta) break;
                    if(clock.checkLimit()) break;
                }
            }
            newTree.root().setScore(minEval);
        }
        return newTree;
    }

    /**
     * @param board the current gamestate
     * @param t the current tree
     * @param incidentMoves the move used to reach the current gamestate
     * @param depth how many levels further to go down tree
     * @param maximising whose turn it is at the current depth
     * @param player who is making the move
     * @param clock measures how long updateTree has been running. If too long, ends early
     * @return an updated gametree without wasting the tree build in the previous turn
     */
    Tree updateTree(Board board, Tree t, List<Move> incidentMoves, int depth,
                    boolean maximising, boolean player, Clock clock){
        if(depth == TREE_DEPTH){
            if(t != null) {
                Node newNode = t.findChild(board);
                if(newNode != null) {
                    t = MyTree.subTree(newNode);
                    //Remove any leftover speculative moves from detective turn
                    if(player){
                        Tree newTree = MyTree.subTree(newNode);
                        for(var e : t.root().edges().entrySet()){
                            if(e.getValue().get(0).source() != mrXLocation)
                                newTree.root().edges().remove(e.getKey());
                        }
                        t = newTree;
                    }
                }
                else
                    t = null;
            }
            if(t == null)
                return buildTree(board, incidentMoves, possibleMrXLocations,
                        Integer.MIN_VALUE, Integer.MAX_VALUE, depth,
                        maximising, player, clock);
        }
        Tree newTree = new MyTree(t.root());
        if(t.root().edges().size() == 0 && depth != 0){
            return buildTree(t.root().board(), incidentMoves, possibleMrXLocations,
                    Integer.MIN_VALUE, Integer.MAX_VALUE, depth,
                    maximising, player, clock);
        }
        if(depth == 0)
            return t;
        else{
            for(var e : t.root().edges().entrySet()){
                Node n = e.getKey();
                List<Move> moves = e.getValue();
                Tree newBranch = MyTree.subTree(n);
                newBranch = updateTree(newBranch.root().board(), newBranch, moves, depth - 1,
                        !maximising, player, clock);
                newTree.addTree(newBranch, moves);
                if(clock.checkLimit()) break;
            }
            //Update score of root node
            int bestScore = maximising ? Integer.MIN_VALUE : Integer.MAX_VALUE;
            for(var e : newTree.root().edges().entrySet()){
                if(maximising)
                    bestScore = Integer.max(bestScore, e.getKey().score());
                else
                    bestScore = Integer.min(bestScore, e.getKey().score());
            }
            newTree.root().setScore(bestScore);
        }
        return newTree;
    }

    /**
     * @return an incredibly inventive AI name
     */
    @Nonnull
    public String name() {
        return "Mr. NullPointerException";
    }

    /**
     * @param board the board containing the current list of available moves
     * @return all of the pieces who are able to make moves - those remaining in the current turn
     */
    List<Piece> findRemaining(Board board){
        List<Piece> remaining = new ArrayList<>();
        for(Move m : board.getAvailableMoves()){
            if(!remaining.contains(m.commencedBy())){
                remaining.add(m.commencedBy());
            }
        }
        return remaining;
    }

    @Nonnull
    public Move pickMove(@Nonnull Board board, @Nonnull AtomicBoolean terminate) {
        if(!moveQueue.isEmpty()){
            Move chosenMove = moveQueue.poll();
            return chosenMove;
        }
        Clock clock = new Clock(TIME_LIMIT);
        boolean player = board.getAvailableMoves().stream().allMatch(x -> x.commencedBy().isMrX());
        if(!player) mrXLocation = -1;
        else mrXLocation = board.getAvailableMoves().asList().get(0).source();
        Board gameState = new BoardToGameState(board, findRemaining(board), mrXLocation);
        gameTree = updateTree(gameState, gameTree, null, TREE_DEPTH,
                player, player, clock);
        Move chosenMove = null;
        int score = gameTree.root().score();
        for(var e : gameTree.root().edges().entrySet()){
            if(e.getKey().score() == score){
                if(!player){
                    moveQueue.addAll(e.getValue());
                    chosenMove = moveQueue.poll();
                }
                else{
                    assert(e.getValue().size() == 1);
                    chosenMove = e.getValue().get(0);
                    mrXLocation = chosenMove.visit(destinationVisitor);
                    possibleMrXLocations = getPossibleMrXLocations(e.getKey().board(), possibleMrXLocations, chosenMove);
                }
                gameTree = MyTree.subTree(e.getKey());
                break;
            }
        }
        return chosenMove;
    }
}