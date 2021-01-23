package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Transport;

import java.util.*;

/**
 * Class containing static methods to help with processing shortest paths on the game's graph
 */
public class Dijkstra {
    /**
     * @param a list to be sorted
     * @param distances array to sort a with
     * @return list containing elements of a in order of corresponding distance (distance[a[i]])
     */
    static List<Integer> mergeSort(List<Integer> a, int[] distances){
        int n = a.size();
        if(n <= 1) return a;
        List<Integer> a1 = mergeSort(a.subList(0, (n / 2)), distances);
        List<Integer> a2 = mergeSort(a.subList((n/2), n), distances);
        List<Integer> b = new ArrayList<>();
        int i = 0, j = 0;
        while (i < a1.size() || j < a2.size()){
            if(i < a1.size() && (j == a2.size() || distances[a1.get(i) - 1] <= distances[a2.get(j) - 1])){
                b.add(a1.get(i));
                i++;
            }
            else{
                b.add(a2.get(j));
                j++;
            }
        }
        return b;
    }

    /**
     * @param graph graph to have algorithm performed on
     * @return a list of arrays for each node in graph, containing the shortest distance to all other nodes
     */
    public static List<int[]> runDijkstraOnGraph(ImmutableValueGraph<Integer, ImmutableSet<Transport>> graph){
        List<int[]> distanceSet = new ArrayList<>();
        for(int n : graph.nodes()){
            distanceSet.add(runDijkstra(graph, n));
        }
        return distanceSet;
    }

    /**
     * @param graph to be traversed by algorithm
     * @param location node to find the distances from
     * @return an array containing the shortest distance from location to that node.
     *         the distance from location to some node v = distances[v - 1].
     */
    static int[] runDijkstra(ImmutableValueGraph<Integer, ImmutableSet<Transport>> graph, int location){
        int nodeCount = graph.nodes().size();
        int source = location;

        int[] distances = new int[nodeCount];
        List<Integer> q = new ArrayList<>();

        for(int i = 0; i < nodeCount; i++){
            distances[i] = Integer.MAX_VALUE;
            if(i == source - 1){
                distances[i] = 0;
                q.add(i + 1);
            }
        }
        q = mergeSort(q, distances);

        while(!q.isEmpty()){
            int u = q.get(0);
            q.remove(0);
            for(var v : graph.adjacentNodes(u)){
                int alt = distances[u - 1] + 1;
                if(alt < distances[v - 1]){
                    //If unvisited, add to queue
                    if(distances[v - 1] == Integer.MAX_VALUE)
                        q.add(v);
                    distances[v - 1] = alt;
                }
            }
            q = mergeSort(q, distances);
        }
        return distances;
    }
}