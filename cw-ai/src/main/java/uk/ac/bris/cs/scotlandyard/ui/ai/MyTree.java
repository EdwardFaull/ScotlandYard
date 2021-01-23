package uk.ac.bris.cs.scotlandyard.ui.ai;

import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.model.Piece.Detective;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Ticket;

import java.util.ArrayList;
import java.util.List;

public class MyTree implements Tree{

    Node root;

    public MyTree(Board x){
        root = new MyNode(x);
    }

    public MyTree(Node r){
        root = new MyNode(r);
    }

    public void addTree(Tree t, Move edge){
        addTree(t, List.of(edge));
    }

    public void addTree(Tree t, List<Move> edge){
        if(root == null)
            root = t.root();
        else
            root.addEdge(t.root(), edge);
    }

    public Node root(){
        return this.root;
    }

    public void addNode(Board b, Move m){
        addNode(b, List.of(m));
    }

    public void addNode(Board b, List<Move> ms){
        Node n = new MyNode(b);
        if(root == null)
            root = n;
        else
            root.addEdge(n, ms);
    }

    /**
     * @param n the node to become the new root
     * @return a new tree with n as its root, retaining all of its original children with identical edges
     */
    public static Tree subTree(Node n){
        Node newRoot = new MyNode(n);
        Tree t = new MyTree(newRoot);
        return t;
    }

    static boolean compareBoards(Board b, Board c){
        if(!b.getPlayers().equals(c.getPlayers()))
            return false;
        for(Piece p : b.getPlayers()){
            if(p.isDetective()){
                int bLocation = b.getDetectiveLocation((Detective)p).get();
                int cLocation = c.getDetectiveLocation((Detective)p).get();
                if(bLocation != cLocation)
                    return false;
            }
            var bTickets = b.getPlayerTickets(p).get();
            var cTickets = c.getPlayerTickets(p).get();
            for(Ticket t : Ticket.values()){
                if(bTickets.getCount(t) != cTickets.getCount(t))
                    return false;
            }
        }
        if(!b.getAvailableMoves().equals(c.getAvailableMoves()))
            return false;
        return true;
    }

    public Node findChild(Board b){
        if(root == null) return null;
        if(compareBoards(root.board(), b)) return root;
        if(root.edges().size() > 0){
            for(var n : root.edges().entrySet()){
                if(compareBoards(n.getKey().board(), b))
                    return n.getKey();
            }
        }
        return null;
    }
}