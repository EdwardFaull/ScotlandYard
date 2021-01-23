package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;
import java.util.*;

import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;
import uk.ac.bris.cs.scotlandyard.model.Model.Observer.*;
import uk.ac.bris.cs.scotlandyard.model.Board.*;

/**
 * cw-model
 * Stage 2: Complete this class
 */
public final class MyModelFactory implements Factory<Model> {

	public final class MyModel implements Model{
		GameState modelState;
		List<Observer> observers;

		public MyModel(GameSetup setup, Player mrX, ImmutableList<Player> detectives){
			MyGameStateFactory factory = new MyGameStateFactory();
			this.modelState = factory.build(setup, mrX, detectives);
			this.observers = new ArrayList<>();
		}

		@Override
		public Board getCurrentBoard() {
			return modelState;
		}

		@Override
		public void registerObserver(@Nonnull Observer observer) {
			if(observer == null) throw new NullPointerException();
			if(observers.contains(observer)) throw new IllegalArgumentException();
			observers.add(observer);
		}

		@Override
		public void unregisterObserver(@Nonnull Observer observer) {
			if(observer == null) throw new NullPointerException();
			if(!observers.contains(observer)) throw new IllegalArgumentException();
			observers.remove(observer);
		}

		@Nonnull
		@Override
		public ImmutableSet<Observer> getObservers() {
			return ImmutableSet.copyOf(observers);
		}

		@Override
		public void chooseMove(@Nonnull Move move) {
			modelState = modelState.advance(move);
			Event event = modelState.getWinner().isEmpty() ? Event.MOVE_MADE : Event.GAME_OVER;
			for(Observer o : observers)
				o.onModelChanged(modelState, event);
		}
	}

	@Nonnull @Override public Model build(GameSetup setup,
	                                      Player mrX,
	                                      ImmutableList<Player> detectives) {
		// TODO
		Model model = new MyModel(setup, mrX, detectives);
		return model;
	}
}
