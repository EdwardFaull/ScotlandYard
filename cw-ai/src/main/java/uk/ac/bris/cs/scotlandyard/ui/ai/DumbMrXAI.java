package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.*;
import uk.ac.bris.cs.scotlandyard.model.Piece.*;
import uk.ac.bris.cs.scotlandyard.model.Move.*;

/**
 * A simple Ai which looks ahead a single move
 */
public class DumbMrXAI implements Ai {

    private Visitor<Integer> destinationVisitor;
    private List<int[]> distances;

    @Override
    public void onStart(){
		destinationVisitor = new FunctionalVisitor<>(x -> x.destination, x -> x.destination2);
        try{
            distances = Dijkstra.runDijkstraOnGraph(ScotlandYard.standardGraph());
        }
        catch(IOException e){
            distances = null;
        }
	}

	@Nonnull @Override public String name() { return "Dumb Mr X AI"; }

	private int getMaximumIndex(List<Integer> score) {
        int n = score.size();
        int largestIndex = -1;
        for(int i = 0; i < n; i++){
            if(largestIndex == -1 || score.get(i) > score.get(largestIndex))
                largestIndex = i;
        }
        return largestIndex;
    }

    private List<Integer> removeUnreachablePoints(List<Integer> score, ImmutableSet<Move> moves){
	    List<Integer> newScore = new ArrayList<>();
	    for(var a : score)
	        newScore.add(-1);
        Visitor<Integer> destinationVisitor = new FunctionalVisitor<>(
                x -> x.destination, x -> x.destination2);
	    for(Move m : moves){
            int destination = m.visit(destinationVisitor);
            newScore.set(destination - 1, score.get(destination - 1));
        }

	    return newScore;
    }

    private int getMedian(List<Integer> a){
	    a.sort(Comparator.comparingInt(i -> i));
	    return a.get(a.size() / 2);
    }

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			@Nonnull AtomicBoolean terminate) {
	    List<Integer> detectiveLocations = new ArrayList<>();
	    for(Piece p : board.getPlayers()){
	        if(p.isDetective()){
	            int detectiveLocation = board.getDetectiveLocation((Detective)p).get();
	            detectiveLocations.add(detectiveLocation);
            }
        }
	    List<Integer> score = new ArrayList<>();
	    //Obtain mean distance from detectives for each point
	    for(int i = 0; i < distances.get(0).length; i++){
            List<Integer> distancesAtSamePoint = new ArrayList<>();
	        for(int j = 0; j < detectiveLocations.size(); j++){
	            int detectiveLocation = detectiveLocations.get(j - 1);
                distancesAtSamePoint.add(distances.get(detectiveLocation)[i]);
            }
	        int median = getMedian(distancesAtSamePoint);
            score.add(median);
        }
	    //Remove scores from points that can't be reached from current position
        score = removeUnreachablePoints(score, board.getAvailableMoves());
	    //Find the best scoring point which can be moved to
        Move chosenMove = null;
        int maximum = getMaximumIndex(score);
        for(Move m : board.getAvailableMoves()){
            if(m.visit(destinationVisitor) == maximum + 1)
                chosenMove = m;
        }
        return chosenMove;
	}
}
