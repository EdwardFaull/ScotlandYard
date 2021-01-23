package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableList;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.model.Piece.Detective;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;

import java.util.*;

public class MyNode implements Node {
    Board b;
    int score;
    Map<Node, List<Move>> edges;

    final int DISTANCE_CONSTANT = 200;
    final int VISIBILITY_CONSTANT = 10;
    final int PRICE_CONSTANT = 10;

    public MyNode(Board x){
        this.b = x;
        this.score = -1;
        this.edges = new HashMap<>();
    }

    public MyNode(Node n){
        this.b = n.board();
        this.score = n.score();
        this.edges = new HashMap<>(Map.copyOf(n.edges()));
    }

    List<Integer> getDetectiveLocations(Board board){
        List<Integer> detectiveLocations = new ArrayList<>();
        for(Piece p : board.getPlayers()){
            if(p.isDetective())
                detectiveLocations.add(board.getDetectiveLocation((Detective)p).get());
        }
        return detectiveLocations;
    }

    int getManoeuvrability(Board board, int location, boolean player){
        List<Ticket> modesOfTransport = new ArrayList<>();
        var graph = board.getSetup().graph;
        for(int v : graph.adjacentNodes(location)){
            Set<Transport> transport = graph.edgeValue(location, v).isPresent() ? graph.edgeValue(location, v).get() : new HashSet<>();
            for(Transport t : transport){
                Ticket ticket = t.requiredTicket();
                if(!modesOfTransport.contains(ticket))
                    modesOfTransport.add(ticket);
            }
        }
        int sum = 0;
        for(Ticket t : modesOfTransport){
            switch(t){
                case TAXI:
                    sum += 5;
                    break;
                case BUS:
                    sum += 10;
                    break;
                case UNDERGROUND:
                    sum += 15;
                    break;
                //A ferry is particularly useful, so increase the score for these locations greatly (though only for mr x)
                case SECRET:
                    sum += player ? 100 : 0;
                    break;
            }
        }
        return sum;
    }

    //Returns the sum of the detectives' distances from mrX
    private int MrXScore(List<Move> edge, Board board, List<Integer> possibleMrXLocations, int mrXLocation){
        int score = 0;

        List<Integer> detectiveLocations = getDetectiveLocations(board);
        for(int d : detectiveLocations){
            int distanceFromMrX = MiniMaxAI.dijkstraDistances.get(d - 1)[mrXLocation - 1];
            if(distanceFromMrX == 0)
                return Integer.MIN_VALUE;
            score -= DISTANCE_CONSTANT / distanceFromMrX;
        }
        //How many locations the detectives think MrX could be in
        int visibility = possibleMrXLocations.size();
        score += visibility * VISIBILITY_CONSTANT;

        //Reduces the likelihood of choosing expensive moves, unless the visibility benefit is greater
        int price = MiniMaxAI.ticketPrice(edge);
        score -= price * PRICE_CONSTANT;

        //Encourages Mr X to get to locations with multiple modes of transport, to allow for a greater number of escape routes
        score += getManoeuvrability(board, mrXLocation, true);

        return score;
    }

    private List<Integer> getTrainLocations(Board board){
        List<Integer> trainLocations = new ArrayList<>();
        var graph = board.getSetup().graph;
        for(int u : graph.nodes()){
            for(int v : graph.adjacentNodes(u)){
                var edge = graph.edgeValue(u, v).get();
                for(Transport t : edge){
                    if(t.requiredTicket().equals(ScotlandYard.Ticket.UNDERGROUND)){
                        trainLocations.add(u);
                        break;
                    }
                }
            }
        }
        return trainLocations;
    }

    List<Integer> createModifiablePossibleLocations(Board board, ImmutableList<Integer> possibleMrXLocations){
        List<Integer> modifiableLocations;
        if(possibleMrXLocations.isEmpty())
            modifiableLocations = getTrainLocations(board);
        else
            modifiableLocations = new ArrayList<>(possibleMrXLocations);
        return modifiableLocations;
    }

    private int DetectiveScore(List<Move> edge, Board board, ImmutableList<Integer> possibleMrXLocations){
        int score = 0;
        List<Integer> modifiableLocations = createModifiablePossibleLocations(board, possibleMrXLocations);

        List<Integer> detectiveLocations = new ArrayList<>();
        for(Piece p : board.getPlayers()){
            if(p.isDetective())
                detectiveLocations.add(board.getDetectiveLocation((Detective)p).get());
        }
        List<Integer> bestDistances = new ArrayList<>();
        for(int d : detectiveLocations){
            int bestDistance = Integer.MAX_VALUE;
            for(int x : modifiableLocations){
                int distance = MiniMaxAI.dijkstraDistances.get(d - 1)[x - 1];
                bestDistance = Integer.min(bestDistance, distance);
            }
            final int finalBestDistance = bestDistance;
            if(finalBestDistance == 0)
                score -= 1000;
            else
                score -= DISTANCE_CONSTANT / finalBestDistance;
            bestDistances.add(bestDistance);
            //modifiableLocations.removeIf(x -> x == finalBestDistance);
            //if(modifiableLocations.isEmpty())
            //    modifiableLocations = createModifiablePossibleLocations(board, possibleMrXLocations);
            score += getManoeuvrability(board, d, false);
        }
        //The more possible locations there are, the harder it is to track Mr X down
        //This should increase the score, so locations with high visibility should be avoided by detectives
        int visibility = possibleMrXLocations.size();
        score += visibility * VISIBILITY_CONSTANT;

        int price = MiniMaxAI.ticketPrice(edge);
        score += price * PRICE_CONSTANT;

        return score;
    }

    public void setScore(List<Move> edge, Board board, boolean player, ImmutableList<Integer> possibleMrXLocations, int mrXLocation){
        if(player){
            this.score = MrXScore(edge, board, possibleMrXLocations, mrXLocation);
        }
        else{
            this.score = DetectiveScore(edge, board, possibleMrXLocations);
        }
    }

    public void setScore(int x){ this.score = x; }

    public void addEdge(Node n, Move m){
        List<Move> newList = new ArrayList<>();
        newList.add(m);
        edges.put(n, newList);
    }

    public void addEdge(Node n, List<Move> ms){
        edges.put(n, ms);
    }

    public Map<Node, List<Move>> edges(){ return this.edges; }

    public int score(){ return this.score; }

    public Board board(){ return this.b; }
}
