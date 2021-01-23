package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableMap;
import uk.ac.bris.cs.scotlandyard.model.*;
import uk.ac.bris.cs.scotlandyard.model.Piece.Detective;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Ticket;
import uk.ac.bris.cs.scotlandyard.model.Move.*;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import java.util.*;

public class BoardToGameState implements Board.GameState {

    final Board board;
    private Player mrX;
    final List<Integer> possibleMrXLocations;
    final ImmutableSet<Piece> remaining;
    final Visitor<Integer> destinationVisitor = new Move.FunctionalVisitor<>((x -> x.destination),(x -> x.destination2));

    public BoardToGameState(Board board, ImmutableSet<Piece> remaining, Player mrX, List<Integer> possibleMrXLocations){
        this.board = board;
        this.remaining = remaining;
        this.mrX = mrX;
        this.possibleMrXLocations = possibleMrXLocations;
    }

    public BoardToGameState(Board board, List<Piece> remaining, int mrXLocation){
        this.board = board;
        this.remaining = ImmutableSet.copyOf(remaining);
        this.mrX = new Player(Piece.MrX.MRX, ImmutableMap.copyOf(ticketBoardToMap(Piece.MrX.MRX)), mrXLocation);
        this.possibleMrXLocations = new ArrayList<>();
    }

    private ImmutableSet<Piece> detectiveWinners(){
        List<Piece> detectiveWinners = new ArrayList<>();
        for(Piece d : board.getPlayers()){
            if(d.isDetective())
                detectiveWinners.add(d);
        }
        return ImmutableSet.copyOf(detectiveWinners);
    }

    //sees if particular detective has no moves left
    private boolean isPlayerInGame(Piece p, ImmutableMap<Ticket, Integer> tickets, int location){
        boolean hasAnyTickets = false;
        //if there are no tickets left
        for(Integer i : tickets.values()){
            if(i != 0) hasAnyTickets = true;
        }
        if(!hasAnyTickets)
            return false;
        //if has no moves left
        return createPlayerMoves(p, location).size() != 0;
    }

    private boolean isPlayerInGame(Player p){
        return isPlayerInGame(p.piece(), p.tickets(), p.location());
    }

    //creates the list of winners, separate from returning it
    private ImmutableSet<Piece> createWinner(List<Piece> newRemaining,
                                             Map<Piece, ImmutableMap<Ticket, Integer>> newTickets,
                                             Map<Detective, Integer> newLocations, Player newMrX){
        boolean areDetectivesStuck = true;
        if(newRemaining.equals(ImmutableSet.of(Piece.MrX.MRX))){
            if(board.getMrXTravelLog().size() == board.getSetup().rounds.size()) return ImmutableSet.of(Piece.MrX.MRX);
            if(!isPlayerInGame(newMrX)) return detectiveWinners();
        }
        for(Piece d : board.getPlayers()){
            if(d.isDetective()){
                if(board.getDetectiveLocation((Detective)d).get() == mrX.location()){
                    return detectiveWinners();
                }
                int location = newLocations.get(d);
                if(isPlayerInGame(d, newTickets.get(d), location))
                    areDetectivesStuck = false;
            }
        }
        if(areDetectivesStuck) return ImmutableSet.of(mrX.piece());
        return ImmutableSet.<Piece>builder().build();
    }

    //Gets a list of SingleMove objects that player p can make
    private List<Move> createSingleMoves(Piece p, int location){
        List<Move> moves = new ArrayList<>();
        var graph = board.getSetup().graph;
        Player player = null;
        if(p.isMrX())
            player = mrX;
        else{
            player = new Player(p, ImmutableMap.copyOf(ticketBoardToMap(p)), location);
        }

        int u = player.location();
        for(int v : graph.adjacentNodes(u)) { //Single moves can only reach neighbour of current node
            var optionalEdge = graph.edgeValue(u, v);
            if (optionalEdge.isPresent()) {
                var edge = optionalEdge.get();
                for (ScotlandYard.Transport tr : edge) { //Multiple methods of transport to each node
                    boolean isMoveAllowed = true;
                    Ticket t = tr.requiredTicket();
                    for (Piece d : board.getPlayers()) { //Detectives cannot overlap
                        if (d.isDetective() && board.getDetectiveLocation((Detective) d).get() == v)
                            isMoveAllowed = false;
                    }
                    if(player.has(t) || (player.isMrX() && player.has(Ticket.SECRET))){
                        if(!player.isMrX() && isMoveAllowed) //Detectives can't use secret tickets
                            moves.add(new SingleMove(p, u, t, v));
                        else{
                            if(isMoveAllowed){
                                if(player.has(t))
                                    moves.add(new SingleMove(p, u, t, v));
                                if(player.has(Ticket.SECRET)) //Secret can be used whenever, so add if mrX has it
                                    moves.add(new SingleMove(p, u, Ticket.SECRET, v));
                            }
                        }
                    }
                }
            }
        }
        return moves;
    }

    //Gets all (single and double) moves that player p can make
    private List<Move> createPlayerMoves(Piece p, int location){
        List<Move> dMoves = new ArrayList<>();
        List<Move> moves = new ArrayList<>(createSingleMoves(p, location)); //Gets all single moves
        if(p.isMrX()){
            if(mrX.has(Ticket.DOUBLE) && board.getSetup().rounds.size() - 1 != board.getMrXTravelLog().size()){
                for(Move m : moves){
                    //m must be a single move (taken from getSingleMoves(), so can cast without worry)
                    int destination = ((SingleMove)m).destination;
                    mrX = mrX.use(m.tickets()).at(destination); //Alter mrX to after performing single move
                    List<Move> newMoves = new ArrayList<>(createSingleMoves(Piece.MrX.MRX, mrX.location())); //Get moves (new) mrX can perform
                    mrX = mrX.give(m.tickets()).at(m.source()); //Revert mrX
                    //Create new DoubleMove objects
                    for(Move n : newMoves){
                        dMoves.add(new DoubleMove(m.commencedBy(), m.source(), ((SingleMove)m).ticket,
                                n.source(), ((SingleMove)n).ticket, ((SingleMove)n).destination));
                    }
                }
            }
        }
        moves.addAll(dMoves);
        return moves;
    }

    public List<Move> createAvailableMoves(List<Piece> newRemaining, Map<Detective, Integer> newLocations, Player newMrX) {
        List<Move> moves = new ArrayList<>();
        if(newRemaining.contains(Piece.MrX.MRX)){ //If mrX's turn, get only their moves
            if(mrX.location() == -1){
                for(int l : possibleMrXLocations){
                    moves.addAll(createPlayerMoves(Piece.MrX.MRX, l));
                }
            }
            else
                moves = createPlayerMoves(Piece.MrX.MRX, mrX.location());
        }
        else{ //If detectives' turn, exclude mrX's moves
            for(Piece d : newRemaining){
                if(d.isDetective())
                    moves.addAll(createPlayerMoves(d, newLocations.get((Detective)d)));
            }
        }
        return moves;
    }

    private Map<Detective, Integer> updateDetectiveLocations(Piece p, int destination){
        var newDetectiveLocations = new HashMap<Detective, Integer>();
        //Add all existing and not moving detectives into new map
        for(Piece d : board.getPlayers()){
            if(d.isDetective()) {
                Optional<Integer> optionalLocation = board.getDetectiveLocation((Detective) d);
                if(optionalLocation.isEmpty())
                    throw new IllegalArgumentException();
                newDetectiveLocations.put((Detective)d, optionalLocation.get());
            }
        }
        //Update moving detective's location
        if(p.isDetective())
            newDetectiveLocations.replace((Detective)p, destination);

        return newDetectiveLocations;
    }

    private Map<Ticket, Integer> ticketBoardToMap(Piece p){
        Map<Ticket, Integer> ticketCounts = new HashMap<>();
        var optionalTickets = board.getPlayerTickets(p);
        if(optionalTickets.isEmpty())
            throw new IllegalArgumentException();
        var ticketBoard = optionalTickets.get();
        for(Ticket t : Ticket.values())
            ticketCounts.put(t, ticketBoard.getCount(t));
        return ticketCounts;
    }

    private Map<Piece, ImmutableMap<Ticket, Integer>> updateTickets(Piece piece, Iterable<Ticket> ticketsUsed){
        var newTickets = new HashMap<Piece, ImmutableMap<Ticket, Integer>>();
        for(Piece p : board.getPlayers()){
            //Create map of tickets and number of said ticket
            var ticketCounts = ticketBoardToMap(p);
            //Take 1 off count for each ticket used by moving piece
            if(p.equals(piece) || p.isMrX() && piece.isDetective()){
                int i = -1;
                if(p.isMrX() && piece.isDetective())
                    i = 1;
                var optionalTickets = board.getPlayerTickets(p);
                if(optionalTickets.isEmpty())
                    throw new IllegalArgumentException();
                var ticketBoard = optionalTickets.get();
                for(Ticket t : ticketsUsed){
                    ticketCounts.replace(t, ticketBoard.getCount(t) + i);
                }
            }
            newTickets.put(p, ImmutableMap.copyOf(ticketCounts));
        }
        return newTickets;
    }

    private List<LogEntry> updateLog(List<LogEntry> newLog, Move move){
        //use visitor to fetch first ticket of move
        Visitor<Ticket> firstTicketVisitor = new FunctionalVisitor<>(x -> x.ticket, x -> x.ticket1);
        Visitor<Integer> firstDestinationVisitor = new FunctionalVisitor<>(x -> x.destination, x-> x.destination1);
        Ticket firstTicket = move.visit(firstTicketVisitor);
        if(board.getSetup().rounds.get(newLog.size()))
            newLog.add(LogEntry.reveal(firstTicket, move.visit(firstDestinationVisitor)));
        else
            newLog.add(LogEntry.hidden(firstTicket));
        //if a double move, get the second ticket
        if(move.visit(new FunctionalVisitor<>(x -> false, x -> true))){
            Ticket secondTicket = ((DoubleMove)move).ticket2;
            if(board.getSetup().rounds.get(newLog.size()))
                newLog.add(LogEntry.reveal(secondTicket, ((DoubleMove)move).destination2));
            else
                newLog.add(LogEntry.hidden(secondTicket));
        }
        return newLog;
    }

    private List<Piece> addDetective(Piece p, List<Piece> newRemaining){
        if(!p.isMrX()) {
            Optional<TicketBoard> ticketBoard = board.getPlayerTickets(p);
            if(ticketBoard.isEmpty())
                throw new IllegalArgumentException();
            int sumOfTickets = 0;
            for(Ticket t : Ticket.values()){
                sumOfTickets += ticketBoard.get().getCount(t);
            }
            if(sumOfTickets > 0)
                newRemaining.add(p);
        }
        return newRemaining;
    }

    private List<Piece> updateRemaining(Piece commencedBy){
        List<Piece> newRemaining = new ArrayList<>();
        //If mrX just went, add all detectives that can still move
        if(commencedBy.isMrX()){
            for(Piece p : board.getPlayers()){
                newRemaining = addDetective(p, newRemaining);
            }
        }
        //If not, remove detective that just went
        //If no detectives left to move, return singleton list of mrX
        else{
            for(Piece p : remaining){
                if(!p.equals(commencedBy))
                    newRemaining = addDetective(p, newRemaining);
            }
            if(newRemaining.size() == 0){
                newRemaining.add(Piece.MrX.MRX);
            }
        }
        return newRemaining;
    }

    private List<Integer> getPossibleMrXLocations(Move m, int roundNumber){
        List<Move> possibleMoves = new ArrayList<>();
        //If there have been no reveal rounds up to this point, no information to go off
        if(board.getSetup().rounds.subList(0, roundNumber).stream().noneMatch(x -> x))
            return new ArrayList<>();
        //If a reveal round, return only the current location
        if(board.getSetup().rounds.get(roundNumber)){
            return List.of(m.visit(destinationVisitor));
        }
        //Add all destinations reachable with the tickets used
        for(int previousPossibleLocation : possibleMrXLocations){
            List<Move> newMoves = createPlayerMoves(Piece.MrX.MRX, previousPossibleLocation);
            for(Move n : newMoves){
                if(n.tickets().equals(m.tickets()))
                    possibleMoves.add(n);
            }
        }
        //If a double move skips over a reveal round
        if(m.visit(new FunctionalVisitor<>(x -> false, x -> true))){
            if(board.getSetup().rounds.get(roundNumber - 1)){
                //Fetches the first destination for a double move
                Visitor<Integer> firstDestinationVisitor = new FunctionalVisitor<>(x -> 0, x -> x.destination1);
                //Remove all moves where destination1 isn't the one revealed
                possibleMoves.removeIf(x -> x.visit(firstDestinationVisitor) != m.visit(firstDestinationVisitor));
            }
        }
        List<Integer> possibleLocations = new ArrayList<>();
        for(Move n : possibleMoves)
            possibleLocations.add(n.visit(destinationVisitor));

        return possibleLocations;
    }

    public GameState forceAdvance(Move move){
        final Piece commencedBy = move.commencedBy();
        final int destination = move.visit(destinationVisitor);

        int mrXLocation;
        Map<Detective, Integer> newDetectiveLocations = new HashMap<>();
        Map<Piece, ImmutableMap<Ticket, Integer>> newTickets = new HashMap<>();
        List<LogEntry> newMrXTravelLog = new ArrayList<>(board.getMrXTravelLog());
        Player newMrX = mrX;

        for(Piece p : board.getPlayers()){
            if(p.equals(commencedBy)){
                newTickets = updateTickets(p, move.tickets());
                newDetectiveLocations = updateDetectiveLocations(p, destination);
                if(!p.isDetective()){
                    mrXLocation = destination;
                    newMrX = new Player(Piece.MrX.MRX, newTickets.get(Piece.MrX.MRX), mrXLocation);
                    newMrXTravelLog = updateLog(newMrXTravelLog, move);
                }
            }
        }
        List<Piece> newRemaining = updateRemaining(commencedBy);
        List<Move> newMoves = createAvailableMoves(newRemaining, newDetectiveLocations, newMrX);
        ImmutableSet<Piece> newWinner = createWinner(newRemaining, newTickets, newDetectiveLocations, newMrX);
        List<Integer> newPossibleMrXLocations = new ArrayList<>(possibleMrXLocations);
        if(commencedBy.isMrX())
            newPossibleMrXLocations = getPossibleMrXLocations(move, newMrXTravelLog.size());

        Board newBoard = new ImmutableBoard(
                board.getSetup(), ImmutableMap.copyOf(newDetectiveLocations),
                ImmutableMap.copyOf(newTickets), ImmutableList.copyOf(newMrXTravelLog),
                newWinner, ImmutableSet.copyOf(newMoves));
        return new BoardToGameState(newBoard, ImmutableSet.copyOf(newRemaining), newMrX, newPossibleMrXLocations);
    }

    @Nonnull @Override
    public GameState advance(Move move) {
        if(!board.getAvailableMoves().contains(move))
            throw new IllegalArgumentException();
        return forceAdvance(move);
    }

    @Nonnull @Override
    public GameSetup getSetup() {
        return board.getSetup();
    }

    @Nonnull @Override
    public ImmutableSet<Piece> getPlayers() {
        return board.getPlayers();
    }

    @Nonnull @Override
    public Optional<Integer> getDetectiveLocation(Piece.Detective detective) {
        return board.getDetectiveLocation(detective);
    }

    @Nonnull @Override
    public Optional<TicketBoard> getPlayerTickets(Piece piece) {
        return board.getPlayerTickets(piece);
    }

    @Nonnull @Override
    public ImmutableList<LogEntry> getMrXTravelLog() {
        return board.getMrXTravelLog();
    }

    @Nonnull @Override
    public ImmutableSet<Piece> getWinner() {
        return board.getWinner();
    }

    @Nonnull @Override
    public ImmutableSet<Move> getAvailableMoves() {
        return board.getAvailableMoves();
    }
}
