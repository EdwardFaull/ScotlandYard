package uk.ac.bris.cs.scotlandyard.ui.ai;

import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;

import java.util.List;

public interface Tree {
     /**
     * @param b the new game board
     * @param ms the moves used to reach the new board
     */
    void addNode(Board b, List<Move> ms);

    /**
     * @param b the new game board
     * @param m the move used to reach the new board
     */
    void addNode(Board b, Move m);

    /**
     * @param t a tree of future game boards to be added
     * @param ms the moves used to reach the root of t from this.root
     */
    void addTree(Tree t, List<Move> ms);

    /**
     * @param t a tree of future game boards to be added
     * @param m the move used to reach the root of t from this.root
     */
    void addTree(Tree t, Move m);

    /**
     * @return obj. ref to the root node of the tree
     */
    Node root();

    /**
     * @param b the board to find
     * @return the node which contains the board b
     */
    Node findChild(Board b);
}
