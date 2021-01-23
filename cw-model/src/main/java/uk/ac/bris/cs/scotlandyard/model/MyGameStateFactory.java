package uk.ac.bris.cs.scotlandyard.model;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Ticket;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Transport;
//Need all classes from Move
import uk.ac.bris.cs.scotlandyard.model.Move.*;
import uk.ac.bris.cs.scotlandyard.model.Piece.Detective;

/**
 * cw-model
 * Stage 1: Complete this class
 */
public final class MyGameStateFactory implements Factory<GameState> {

	private final class MyGameState implements GameState{
		private GameSetup setup;
		private ImmutableSet<Piece> remaining;
		private ImmutableList<LogEntry> log;
		private Player mrX;
		private List<Player> detectives;
		private ImmutableList<Player> everyone;
		private ImmutableSet<Move> moves;
		private ImmutableSet<Piece> winner;

		private MyGameState(final GameSetup setup,
							final ImmutableSet<Piece> remaining,
							final ImmutableList<LogEntry> log,
							final Player mrX,
							final List<Player> detectives) {
			this.setup = setup;
			this.remaining = remaining;
			this.log = log;
			this.mrX = mrX;
			this.detectives = detectives;
			this.moves = createAvailableMoves();
            this.winner = createWinner();
            if(!winner.isEmpty())
                this.moves = ImmutableSet.<Move>builder().build();

			List<Player> players = new ArrayList<>();
			players.add(mrX);
			players.addAll(detectives);

			everyone = ImmutableList.copyOf(players);
		}

		class MyTicketBoard implements Board.TicketBoard
		{
			ImmutableMap<ScotlandYard.Ticket, Integer> tickets;

			public MyTicketBoard(ImmutableMap<ScotlandYard.Ticket, Integer> tickets){
				this.tickets = tickets;
			}

			@Override
			public int getCount(@Nonnull ScotlandYard.Ticket ticket){
				if(!tickets.containsKey(ticket)) throw new IllegalArgumentException();
				return tickets.get(ticket);
			}
		}

		@Override
		public GameSetup getSetup() {
			return setup;
		}

		@Override
		public ImmutableSet<Piece> getPlayers() {
			List<Piece> pieces = new ArrayList<>();
			for(Player p : everyone){
				pieces.add(p.piece());
			}
			return ImmutableSet.copyOf(pieces);
		}

		@Override
		public Optional<Integer> getDetectiveLocation(Detective detective) {
			for(Player d : detectives){
				if(d.piece().equals(detective)){
					return Optional.of(d.location());
				}
			}
			return Optional.empty();
		}

		@Override
		public Optional<TicketBoard> getPlayerTickets(Piece piece){
			for(Player p : everyone){
				if (p.piece().equals(piece)) {
					TicketBoard tb = new MyTicketBoard(p.tickets());
					return Optional.of(tb);
				}
			}
			return Optional.empty();
		}

		@Override
		public ImmutableList<LogEntry> getMrXTravelLog() {
			return log;
		}

		private ImmutableSet<Piece> detectiveWinners(){
            List<Piece> detectiveWinners = new ArrayList<>();
            for(Player e : detectives){
                detectiveWinners.add(e.piece());
            }
            return ImmutableSet.copyOf(detectiveWinners);
        }

        //sees if particular detective has no moves left
        private boolean isPlayerInGame(Player p){
			boolean hasAnyTickets = false;
			ImmutableMap<Ticket, Integer> tickets = p.tickets();
			//if there are no tickets left
			for(Integer i : tickets.values()){
				if(i != 0) hasAnyTickets = true;
			}
			if(!hasAnyTickets)
				return false;
			//if has no moves left
			return createPlayerMoves(p).size() != 0;
		}

		//creates the list of winners, separate from returning it
        private ImmutableSet<Piece> createWinner(){
            boolean isMrXWon = true;
            if(remaining.equals(ImmutableSet.of(mrX.piece()))){
                if(log.size() == setup.rounds.size()) return ImmutableSet.of(mrX.piece());
                if(!isPlayerInGame(mrX)) return detectiveWinners();
            }
            for(Player d : detectives){
                if(d.location() == mrX.location()){
                    return detectiveWinners();
                }
				if(isPlayerInGame(d)) isMrXWon = false;
            }
            if(isMrXWon) return ImmutableSet.of(mrX.piece());

            return ImmutableSet.<Piece>builder().build();
        }

        //returns the winner list
		@Override
		public ImmutableSet<Piece> getWinner() {
		    return winner;
		}

		//Gets a list of SingleMove objects that player p can make
		private List<Move> createSingleMoves(Player p){
			List<Move> moves = new ArrayList<>();
			int u = p.location();
			for(int v : setup.graph.adjacentNodes(u)) { //Single moves can only reach neighbour of current node
				var optionalEdge = setup.graph.edgeValue(u, v);
				if (optionalEdge.isPresent()) {
					var edge = optionalEdge.get();
					for (Transport tr : edge) { //Multiple methods of transport to each node
						boolean isMoveAllowed = true;
						Ticket t = tr.requiredTicket();
						for (Player d : detectives) { //Detectives cannot overlap
							if (d.location() == v)
								isMoveAllowed = false;
						}
						if(p.has(t) || (p.isMrX() && p.has(Ticket.SECRET))){
							if(!p.isMrX() && isMoveAllowed) //Detectives can't use secret tickets
								moves.add(new SingleMove(p.piece(), u, t, v));
							else{
								if(isMoveAllowed){
									if(p.has(t))
										moves.add(new SingleMove(p.piece(), u, t, v));
									if(p.has(Ticket.SECRET)) //Secret can be used whenever, so add if mrX has it
										moves.add(new SingleMove(p.piece(), u, Ticket.SECRET, v));
								}
							}
						}
					}
				}
			}
			return moves;
		}

		//Gets all (single and double) moves that player p can make
		private List<Move> createPlayerMoves(Player p){
			List<Move> dMoves = new ArrayList<>();
			List<Move> moves = new ArrayList<>(createSingleMoves(p)); //Gets all single moves
			if(p.isMrX()){
				if(mrX.has(Ticket.DOUBLE) && setup.rounds.size() - 1 != log.size()){
					for(Move m : moves){
						//m must be a single move (taken from getSingleMoves(), so can cast without worry)
						int destination = ((SingleMove)m).destination;
						mrX = mrX.use(m.tickets()).at(destination); //Alter mrX to after performing single move
						List<Move> newMoves = new ArrayList<>(createSingleMoves(mrX)); //Get moves (new) mrX can perform
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

		public ImmutableSet<Move> createAvailableMoves() {
			List<Move> moves = new ArrayList<>();
			if(remaining.contains(mrX.piece())){ //If mrX's turn, get only their moves
				moves = createPlayerMoves(mrX);
			}
			else{ //If detectives' turn, exclude mrX's moves
				for(Player d : detectives){
				    if(remaining.contains(d.piece()))
					    moves.addAll(createPlayerMoves(d));
				}
			}
			return ImmutableSet.copyOf(moves);
		}

		@Override
		public ImmutableSet<Move> getAvailableMoves(){
			return moves;
		}

		//creates list of either detectives who haven't gone this round yet OR Mr X
		public ImmutableSet<Piece> createRemaining(Piece piece){
			List<Piece> newRemaining = new ArrayList<>();
			//adding detectives to list
			if(piece.isMrX()){
				for(Player d : detectives)
					newRemaining.add(d.piece());
			}
			else{
				//if all the detectives have gone
				if(remaining.size() == 1)
					newRemaining.add(mrX.piece());
				else{
					//if there are detectives remaining, remove only the current player
					for(Player d : detectives){
						if(remaining.contains(d.piece())){
							if(!d.piece().equals(piece) && isPlayerInGame(d))
								newRemaining.add(d.piece());
						}
					}
					//if no detectives left can move, go to Mr X's turn
					if(newRemaining.isEmpty())
						newRemaining.add(mrX.piece());
				}
			}
			return ImmutableSet.copyOf(newRemaining);
		}

		public ImmutableList<LogEntry> updateLog(Move move){
			List<LogEntry> newLog = new ArrayList<>(log);
			//use visitor to fetch first ticket of move
			Visitor<Ticket> firstTicketVisitor = new FunctionalVisitor<>(x -> x.ticket, x -> x.ticket1);
			//Fetches mrX location after first move
			Visitor<Integer> firstDestinationVisitor = new FunctionalVisitor<>(x -> x.destination, x -> x.destination1);
			Ticket firstTicket = move.visit(firstTicketVisitor);
			if(setup.rounds.get(newLog.size()))
				newLog.add(LogEntry.reveal(firstTicket, move.visit(firstDestinationVisitor)));
			else
				newLog.add(LogEntry.hidden(firstTicket));
			//if a double move, get the second ticket
			if(move.visit(new FunctionalVisitor<>(x -> false, x -> true))){
				Ticket secondTicket = ((DoubleMove)move).ticket2;
				if(setup.rounds.get(newLog.size()))
					newLog.add(LogEntry.reveal(secondTicket, ((DoubleMove)move).destination2));
				else
					newLog.add(LogEntry.hidden(secondTicket));
			}
			return ImmutableList.copyOf(newLog);
		}

		private List<Player> updateDetectives(Player p){
			List<Player> newDetectives = new ArrayList<>();
			for(Player d : detectives){
				if(!d.piece().equals(p.piece()))
					newDetectives.add(d);
			}
			newDetectives.add(p);
			return newDetectives;
		}

		@Override
		public GameState advance(Move move) {
		    final Piece piece = move.commencedBy();
		    Player newMrX = mrX;
		    List<Player> newDetectives = detectives;
		    List<LogEntry> newLog = log;
		    Set<Piece> newRemaining = remaining;

		    //check move is legal
		    if(!moves.contains(move))
		    	throw new IllegalArgumentException();
		    //Visitor to fetch destination of move
			Visitor<Integer> v = new FunctionalVisitor<>((x -> x.destination),(x -> x.destination2));
			//Update player location and tickets
		    for(Player p : everyone){
				if(p.piece().equals(piece)){
					if(!remaining.contains(p.piece()))
						throw new IllegalArgumentException();
					p = p.at(move.visit(v));
					p = p.use(move.tickets());
					if(!p.isMrX()){
						newMrX = mrX.give(move.tickets());
						newDetectives = updateDetectives(p);
						//mrX = mrX.give(move.tickets());
						//detectives = updateDetectives(p);
					}
					else{
						newMrX = p;
						newLog = updateLog(move);
						//mrX = p;
						//log = updateLog(move);
					}
				}
		    }
		    newRemaining = createRemaining(piece);
			//return new MyGameState(setup, remaining, log, mrX, detectives);
			return new MyGameState(setup, ImmutableSet.copyOf(newRemaining),
					ImmutableList.copyOf(newLog), newMrX, newDetectives);
		}
	}

	private boolean enforceRules(final GameSetup setup, final ImmutableList<Player> detectives){
		if(setup.rounds.isEmpty()) return false;
		if(setup.graph.nodes().isEmpty()) return false;
		for(final Player d : detectives){
			if(d.has(Ticket.DOUBLE)) return false;
			if(d.has(Ticket.SECRET)) return false;
			for(final Player e : detectives){
				if(d != e) {
					if(d.equals(e)) return false;
					if(d.piece().equals(e.piece())) return false;
					if (d.location() == e.location()) return false;
				}
			}
		}
		return true;
	}

	@Nonnull @Override public GameState build(
			GameSetup setup,
			Player mrX,
			ImmutableList<Player> detectives){
		if(!enforceRules(setup, detectives)) throw new IllegalArgumentException();

		ImmutableList<LogEntry> log = ImmutableList.<LogEntry>builder().build();
		ImmutableSet<Piece> remaining = ImmutableSet.of(mrX.piece());

		return new MyGameState(setup, remaining, log, mrX, detectives);

	}

}
