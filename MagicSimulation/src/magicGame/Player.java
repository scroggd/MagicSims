package magicGame;

import java.util.ArrayList;

public class Player {

	String[] deckList;
	ArrayList<Card> deck;
	ArrayList<Card> hand;
	// Have I lost the game?
	boolean hasLost;
	int life;
	GameState gameState;
	// Have I played a land this turn?
	boolean playedLand;
	int playerNumber;

	// Mana open is a length 6 int array that stores avaliable mana in
	// alphabetical color order, with colorless last. The specific order is
	// [Black, Blue, Green, White, Red, Colorless]
	private int[] manaOpen;
	private ArrayList<Card> graveyard;

	public Player(String[] newDeck, GameState gameState) {
		this.hasLost = false;
		playedLand = false;
		this.life = 20;
		this.deckList = newDeck;
		deck = new ArrayList<Card>();
		hand = new ArrayList<Card>();
		this.gameState = gameState;
		this.manaOpen = new int[6];
		this.graveyard = new ArrayList<Card>();

		for (String cardName : this.deckList) {
			Card card = CardDB.getCard(cardName, this);
			this.deck.add(card);
		}

	}

	public ArrayList<Card> getDeck() {
		return this.deck;
	}

	public ArrayList<Card> getHand() {
		return this.hand;
	}

	public boolean draw(int numToDraw) {
		for (int i = 0; i < numToDraw; i++) {
			if (deck.size() == 0) {
				this.hasLost = true;
				return this.hasLost;
			}
			this.hand.add(this.deck.remove(0));
		}
		return this.hasLost;
	}

	public boolean checkHasLost() {
		return hasLost;
	}

	public void setHasLost(boolean hasLost) {
		this.hasLost = hasLost;
	}

	public int getLife() {
		return this.life;
	}

	public int setLife(int newLife) {
		this.life = newLife;
		return this.life;
	}

	public int changeLife(int lifeChangeIncrement) {
		this.life += lifeChangeIncrement;
		return this.life;
	}

	// For now, this is just going to declare a creature blocking for each
	// attacking creature, in order from greatest attacking power to least. It
	// will consider things like optimal combat outcome later, after I've tested
	// the basic code.
	public void chooseBlockers(ArrayList<Card> controlledCreatures,
			ArrayList<Card> creaturesAttackingMe) {
		// TODO Make blocking actually make good decisions.
		// TODO: Edit creatures so that they know who they are attacking and
		// blocking, so that GameState can do damage resolution better.
		int highestAttackingPower = -2;
		Card highestPowerAttacker = null;
		while (creaturesAttackingMe.size() > 0
				&& controlledCreatures.size() > 0) {
			for (Card creature : controlledCreatures) {
				if (creature.getPower() > highestAttackingPower) {
					highestAttackingPower = creature.getPower();
					highestPowerAttacker = creature;
				}
			}
			Card creature = controlledCreatures.remove(0);
			highestPowerAttacker.setBlockedBy(creature);
			creature.setBlocking(highestPowerAttacker);
			highestPowerAttacker.setBlocked(true);
			creaturesAttackingMe.remove(highestPowerAttacker);
		}

	}

	// the main phase is where most stuff happens, like playing lands and
	// casting creature cards and other spells. Right now, all it does is play
	// lands and creatures - in the future, Sorcery speed cards will be played
	// here, and there will be heuristics for skipping on playing cards to be
	// able to play instants on the opponent's turn.
	public void playMainPhase() {
		// Look for a land to play, and play it.
		Card c;
		for (int i = 0; i < this.hand.size(); i++) {
			c = this.hand.get(i);
			if (c.getTypes().equals("Land") && this.playedLand == false) {
				this.playPermanentCard(i);
				this.playedLand = true;
				break;
			}

		}

		this.evaluateOpenMana();

		// Evaluate playable cards, and play the creature with the most power,
		// if possible. No creature in magic has a power less than or equal to
		// -2, so we'll start with that default value.
		while (true) {
			int maxPowerIndex = -1;
			int maxPower = -2;
			for (int i = 0; i < this.hand.size(); i++) {
				c = this.hand.get(i);
				if (c.getTypes().equals("Creature")) {
					if ((c.getPower() > maxPower)
							&& this.evaluateCardPlayable(c)) {

						maxPower = c.getPower();
						maxPowerIndex = i;
					}
				}
			}

			// Makes sure we found a creature and plays it, then breaks the loop
			// if we didn't find a creature to play. This is, of course,
			// assuming that only creature cards are in the game. This will be
			// fixable and generalizable later on.
			if (maxPowerIndex > -1) {
				this.playPermanentCard(maxPowerIndex);
				this.evaluateOpenMana();
			} else {
				break;
			}
		}
	}

	private void playPermanentCard(int handIndex) {
		Card cardToPlay = this.hand.remove(handIndex);
		this.payCost(cardToPlay);
		this.gameState.addPermanent(cardToPlay);
		System.out.println("Player " + this.playerNumber + " has played "
				+ cardToPlay.getName() + ".");

	}

	// So I wasn't actually paying the cost for cards. Whoops. This method
	// re-evaluates the mana cost, then taps lands accordingly. This will have
	// to be majorly overhaulled when I implement mana dorks and mana artifacts.
	private void payCost(Card cardToPlay) {
		int[] manaCost = this.evaluateCardCost(cardToPlay);

		int specificMana = 0;
		for (int i = 0; i < 6; i++) {
			specificMana = manaCost[i];
			switch (i) {
			case 0:
				for (int j = 0; j < specificMana; j++) {
					this.gameState.getNextUntappedPerm("Swamp", this)
							.setTapped(true);
				}
				break;
			case 1:
				for (int j = 0; j < specificMana; j++) {
					this.gameState.getNextUntappedPerm("Island", this)
							.setTapped(true);
				}
				break;
			case 2:
				for (int j = 0; j < specificMana; j++) {
					this.gameState.getNextUntappedPerm("Forest", this)
							.setTapped(true);
				}
				break;
			case 3:
				for (int j = 0; j < specificMana; j++) {
					this.gameState.getNextUntappedPerm("Mountain", this)
							.setTapped(true);
				}
				break;
			case 4:
				for (int j = 0; j < specificMana; j++) {
					this.gameState.getNextUntappedPerm("Plains", this)
							.setTapped(true);
				}
				break;
			case 5:
				break; // Some slightly complicated logic will end up going
						// here. For now, I'm just going to tap the next
						// avaliable land that I control, as bad an idea as that
						// is in practice.

			}

		}

	}

	private int[] evaluateCardCost(Card cardToPlay) {
		// The mana cost array looks at mana in alphabetical order for colors,
		// with colorless last. The specific order is [Black, Blue, Green, Red,
		// White, Colorless].
		int[] manaCost = { 0, 0, 0, 0, 0, 0 };

		// This will have to change a bit when I introduce hybrid mana and
		// alternate costs. It's fine for the current mono green implementation
		// though.
		for (char ch : cardToPlay.getCost().toCharArray()) {
			switch (ch) {
			case 'B':
				manaCost[0]++;
				break;
			case 'U':
				manaCost[1]++;
				break;
			case 'G':
				manaCost[2]++;
				break;
			case 'R':
				manaCost[3]++;
				break;
			case 'W':
				manaCost[4]++;
				break;
			case '1':
				manaCost[5]++;
				break;
			default:
				break;
			}
		}

		return manaCost;
	}

	// This determines the mana cost of the card via the same method that the
	// evaluateOpenMana evaluates avaliable mana, but using the cost string that
	// each card has, instead of building up a fresh string.
	private boolean evaluateCardPlayable(Card c) {
		// There are spells that have no cost, and those cannot be played for
		// their cost, seeing as they don't have one. Thus, despite not
		// implementing those cards for a while, there will be a special case
		// where I check to see if their cost is null.

		if (c.getCost().equals("0")) {
			return false;
		}

		// I've found that having the card's mana cost by itself is a desirable
		// thing, so I made a separate method for it.

		int[] manaCost = this.evaluateCardCost(c);

		// Here I check each of the five colors, and ensure that I have enough
		// mana open to pay for the colored costs.
		for (int i = 0; i < 5; i++) {
			if (this.manaOpen[i] < manaCost[i]) {
				return false;
			}
		}
		// This part is only reachable if all the colored costs can be payed.
		// Here it checks the colorless cost against avaliable colorless mana
		// and all left over colored mana.

		int remainingMana = 0;
		for (int i = 0; i < 5; i++) {
			remainingMana += (this.manaOpen[i] - manaCost[i]);
		}

		// Add in colorless mana and check total remaining mana against
		// colorless cost. Return false if mana insufficient, otherwise pass to
		// a default return true, which is the default because zero mana stuff
		// exists.
		remainingMana += this.manaOpen[5];
		if (remainingMana < manaCost[5]) {
			return false;
		}

		return true;
	}

	// Here, we evaluate open mana by building a string of open mana and
	// parsing it into integers on a case-by-case basis.
	private void evaluateOpenMana() {
		String openMana = this.gameState.openMana(this);
		this.manaOpen = new int[6];
		for (char ch : openMana.toCharArray()) {
			switch (ch) {
			case 'B':
				this.manaOpen[0]++;
				break;
			case 'U':
				this.manaOpen[1]++;
				break;
			case 'G':
				this.manaOpen[2]++;
				break;
			case 'R':
				this.manaOpen[3]++;
				break;
			case 'W':
				this.manaOpen[4]++;
				break;
			case '1':
				this.manaOpen[5]++;
				break;
			default:
				break;

			}

		}

	}

	// Here the player shuffle's the player's library(deck). This is implemented
	// in constant time by making a new ArrayList<Card> and removing cards from
	// it in a random order, putting them into the new construct, which when
	// full is put in this.deck.
	public void shuffle() {
		ArrayList<Card> shuffledDeck = new ArrayList<Card>();
		while (this.deck.size() > 0) {
			shuffledDeck.add(this.deck.remove((int) (Math.random() * this.deck
					.size())));
		}
		this.deck = shuffledDeck;
	}

	// Takes an array of controlled creatures, and tells them all to attack by
	// tapping them and then sending back to the GameState the list of creatures
	// that are attacking. More complex logic will go in here later, when I
	// start REALLY simulating games. I've also added in that it assigns
	// creatures in order to players on the players list. With only a two-player
	// game, it will always attack the opponent.
	public ArrayList<Card> chooseAttackers(ArrayList<Card> controlledCreatures) {
		int playerIndex = 0;
		for (Card creature : controlledCreatures) {
			creature.setTapped(true);
			creature.setAttacking(true);

			// Makes sure I'm not trying to attack myself.
			if (this.gameState.getPlayers().get(playerIndex)
					.equals(this.gameState.getActivePlayer())) {
				playerIndex++;
			}
			// Checks to avoid array out of bounds errors.
			if (playerIndex >= this.gameState.getNumPlayers()) {
				playerIndex = 0;

			}
			creature.setDefendingPlayer(this.gameState.getPlayers().get(
					playerIndex));
		}
		// TODO: create real logic for attacking
		return controlledCreatures;

	}

	public void destroy(Card creature) {
		System.out.println(creature.getName() + " is about to be destroyed.");
		this.graveyard.add(creature);
		System.out.println(creature.getName() + " has been destroyed.");

	}

	public int getPlayerNumber() {
		return playerNumber;
	}

	public void setPlayerNumber(int playerNumber) {
		this.playerNumber = playerNumber;
	}

	public boolean isPlayedLand() {
		return playedLand;
	}

	public void setPlayedLand(boolean playedLand) {
		this.playedLand = playedLand;
	}

}
