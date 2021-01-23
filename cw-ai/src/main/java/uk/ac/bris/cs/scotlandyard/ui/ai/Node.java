package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableList;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;

import java.util.List;
import java.util.Map;

public interface Node {
    /**
     * @param n node to be linked to from this
     * @param edge move that is used to travel from this to n
     */
    void addEdge(Node n, Move edge);

    /**
     * @param n node to be linked to from this
     * @param edge moves that are used to travel from this to n
     */
    void addEdge(Node n, List<Move> edge);

    /**
     * @param edge moves used to get to 'this'
     * @param board advanced gamestate
     * @param player which method to use for scoring, Mr X if true and Detectives if false
     * @param possibleMrXLocations the positions that the detectives speculate Mr X could be in
     * @param mrXLocation Mr X's actual location; -1 if detectives
     */
    void setScore(List<Move> edge, Board board, boolean player, ImmutableList<Integer> possibleMrXLocations, int mrXLocation);

    /**
     * Method to set score of branching nodes
     * @param x best score chosen from children
     */
    void setScore(int x);

    /**
     * @return gets the edges coming from the node
     */
    Map<Node, List<Move>> edges();

    /**
     * @return gets the current score of the node
     */
    int score();

    /**
     * @return gets the game board of the node
     */
    Board board();
}
