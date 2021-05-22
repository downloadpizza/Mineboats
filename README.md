# Mineboats

## Tutorial
Works with client side commands:
- line
  - start: sets pos1 of the "line"
  - end: sets pos2 of the "line"

"line" refers to the detection block used to decide if a player is passing the finish line

- connect

connects to a livesplit server running on the default port, note that livesplit server is not installed in default livesplit.

- splits
  - start: starts the splits when the player moves
  - startonsplit: starts when the player first crosses the line
  - stop: stops all splitting operations

## CubeKrowd rule compliance
The mod tracks the player position and speed. No other information is used.
