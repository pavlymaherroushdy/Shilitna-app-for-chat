package com.example.games

import kotlin.math.abs

enum class ChessColor {
  WHITE,
  BLACK;

  fun opposite(): ChessColor = if (this == WHITE) BLACK else WHITE
}

enum class PieceType(val value: Int) {
  PAWN(10),
  KNIGHT(30),
  BISHOP(32),
  ROOK(50),
  QUEEN(90),
  KING(900)
}

data class ChessPiece(
  val type: PieceType,
  val color: ChessColor
) {
  fun symbol(): String = when (color) {
    ChessColor.WHITE -> when (type) {
      PieceType.KING -> "♔"
      PieceType.QUEEN -> "♕"
      PieceType.ROOK -> "♖"
      PieceType.BISHOP -> "♗"
      PieceType.KNIGHT -> "♘"
      PieceType.PAWN -> "♙"
    }
    ChessColor.BLACK -> when (type) {
      PieceType.KING -> "♚"
      PieceType.QUEEN -> "♛"
      PieceType.ROOK -> "♜"
      PieceType.BISHOP -> "♝"
      PieceType.KNIGHT -> "♞"
      PieceType.PAWN -> "♟"
    }
  }
}

data class Square(val row: Int, val col: Int)

data class ChessSnapshot(
  val board: List<List<ChessPiece?>>,
  val turn: ChessColor,
  val capturedByWhite: List<ChessPiece>,
  val capturedByBlack: List<ChessPiece>,
  val moveCount: Int
)

data class ChessGameState(
  val board: List<List<ChessPiece?>> = ChessEngine.initialBoard(),
  val turn: ChessColor = ChessColor.WHITE,
  val selected: Square? = null,
  val legalTargets: Set<Square> = emptySet(),
  val capturedByWhite: List<ChessPiece> = emptyList(),
  val capturedByBlack: List<ChessPiece> = emptyList(),
  val inCheck: ChessColor? = null,
  val winner: ChessColor? = null,
  val isDraw: Boolean = false,
  val moveCount: Int = 0,
  val lastMoveFrom: Square? = null,
  val lastMoveTo: Square? = null,
  val history: List<ChessSnapshot> = emptyList()
)

object ChessEngine {

  fun initialBoard(): List<List<ChessPiece?>> {
    val backRank = listOf(
      PieceType.ROOK,
      PieceType.KNIGHT,
      PieceType.BISHOP,
      PieceType.QUEEN,
      PieceType.KING,
      PieceType.BISHOP,
      PieceType.KNIGHT,
      PieceType.ROOK
    )
    return List(8) { row ->
      List(8) { col ->
        when (row) {
          0 -> ChessPiece(backRank[col], ChessColor.BLACK)
          1 -> ChessPiece(PieceType.PAWN, ChessColor.BLACK)
          6 -> ChessPiece(PieceType.PAWN, ChessColor.WHITE)
          7 -> ChessPiece(backRank[col], ChessColor.WHITE)
          else -> null
        }
      }
    }
  }

  fun onSquareTapped(state: ChessGameState, square: Square): ChessGameState {
    if (state.winner != null || state.isDraw) return state

    val selected = state.selected
    if (selected != null && square in state.legalTargets) {
      return applyMove(state, selected, square)
    }

    val tappedPiece = state.board[square.row][square.col]
    if (tappedPiece != null && tappedPiece.color == state.turn) {
      val legal = legalMovesFor(state.board, square, state.turn)
      return state.copy(selected = square, legalTargets = legal)
    }

    return state.copy(selected = null, legalTargets = emptySet())
  }

  fun applyMove(state: ChessGameState, from: Square, to: Square): ChessGameState {
    val movingPiece = state.board[from.row][from.col] ?: return state
    if (movingPiece.color != state.turn) return state
    val legal = legalMovesFor(state.board, from, state.turn)
    if (to !in legal) return state

    val snapshot = ChessSnapshot(
      board = state.board,
      turn = state.turn,
      capturedByWhite = state.capturedByWhite,
      capturedByBlack = state.capturedByBlack,
      moveCount = state.moveCount
    )

    val captured = state.board[to.row][to.col]
    val promotedPiece = if (
      movingPiece.type == PieceType.PAWN &&
      ((movingPiece.color == ChessColor.WHITE && to.row == 0) ||
        (movingPiece.color == ChessColor.BLACK && to.row == 7))
    ) {
      movingPiece.copy(type = PieceType.QUEEN)
    } else {
      movingPiece
    }

    val newBoard = state.board.mapIndexed { r, rowList ->
      rowList.mapIndexed { c, cell ->
        when {
          r == from.row && c == from.col -> null
          r == to.row && c == to.col -> promotedPiece
          else -> cell
        }
      }
    }

    val nextTurn = state.turn.opposite()
    val newCapturedWhite = if (captured != null && state.turn == ChessColor.WHITE) {
      state.capturedByWhite + captured
    } else state.capturedByWhite
    val newCapturedBlack = if (captured != null && state.turn == ChessColor.BLACK) {
      state.capturedByBlack + captured
    } else state.capturedByBlack

    val nextInCheck = if (isKingInCheck(newBoard, nextTurn)) nextTurn else null
    val hasAnyLegal = hasAnyLegalMove(newBoard, nextTurn)

    val winner = if (!hasAnyLegal && nextInCheck != null) state.turn else null
    val isDraw = !hasAnyLegal && nextInCheck == null

    return ChessGameState(
      board = newBoard,
      turn = nextTurn,
      selected = null,
      legalTargets = emptySet(),
      capturedByWhite = newCapturedWhite,
      capturedByBlack = newCapturedBlack,
      inCheck = nextInCheck,
      winner = winner,
      isDraw = isDraw,
      moveCount = state.moveCount + 1,
      lastMoveFrom = from,
      lastMoveTo = to,
      history = (state.history + snapshot).takeLast(30)
    )
  }

  fun undoMove(state: ChessGameState): ChessGameState {
    val prev = state.history.lastOrNull() ?: return state
    val inCheck = if (isKingInCheck(prev.board, prev.turn)) prev.turn else null
    return ChessGameState(
      board = prev.board,
      turn = prev.turn,
      capturedByWhite = prev.capturedByWhite,
      capturedByBlack = prev.capturedByBlack,
      inCheck = inCheck,
      moveCount = prev.moveCount,
      history = state.history.dropLast(1)
    )
  }

  fun pickBotMove(state: ChessGameState): Pair<Square, Square>? {
    if (state.winner != null || state.isDraw) return null
    val color = state.turn
    val candidates = mutableListOf<Triple<Square, Square, Int>>()
    for (r in 0..7) {
      for (c in 0..7) {
        val piece = state.board[r][c] ?: continue
        if (piece.color != color) continue
        val from = Square(r, c)
        for (to in legalMovesFor(state.board, from, color)) {
          val captured = state.board[to.row][to.col]
          var score = (captured?.type?.value ?: 0) * 10
          // Prefer center control slightly
          if (to.row in 3..4 && to.col in 3..4) score += 8
          // Bonus for checking opponent
          val testState = simulateBoard(state.board, from, to, piece)
          if (isKingInCheck(testState, color.opposite())) score += 15
          candidates.add(Triple(from, to, score))
        }
      }
    }
    if (candidates.isEmpty()) return null
    val maxScore = candidates.maxOf { it.third }
    val bestMoves = candidates.filter { it.third == maxScore }
    val chosen = bestMoves.random()
    return chosen.first to chosen.second
  }

  fun legalMovesFor(board: List<List<ChessPiece?>>, from: Square, turn: ChessColor): Set<Square> {
    val piece = board[from.row][from.col] ?: return emptySet()
    if (piece.color != turn) return emptySet()
    val pseudo = pseudoLegalMoves(board, from, piece)
    return pseudo.filter { to ->
      val nextBoard = simulateBoard(board, from, to, piece)
      !isKingInCheck(nextBoard, turn)
    }.toSet()
  }

  private fun hasAnyLegalMove(board: List<List<ChessPiece?>>, color: ChessColor): Boolean {
    for (r in 0..7) {
      for (c in 0..7) {
        val p = board[r][c] ?: continue
        if (p.color == color && legalMovesFor(board, Square(r, c), color).isNotEmpty()) {
          return true
        }
      }
    }
    return false
  }

  fun isKingInCheck(board: List<List<ChessPiece?>>, kingColor: ChessColor): Boolean {
    var kingSquare: Square? = null
    for (r in 0..7) {
      for (c in 0..7) {
        val p = board[r][c]
        if (p != null && p.color == kingColor && p.type == PieceType.KING) {
          kingSquare = Square(r, c)
          break
        }
      }
    }
    val target = kingSquare ?: return false
    val attackerColor = kingColor.opposite()
    for (r in 0..7) {
      for (c in 0..7) {
        val p = board[r][c] ?: continue
        if (p.color == attackerColor) {
          if (target in pseudoLegalMoves(board, Square(r, c), p)) {
            return true
          }
        }
      }
    }
    return false
  }

  private fun pseudoLegalMoves(
    board: List<List<ChessPiece?>>,
    from: Square,
    piece: ChessPiece
  ): List<Square> {
    val moves = mutableListOf<Square>()
    val r = from.row
    val c = from.col

    fun inBounds(nr: Int, nc: Int) = nr in 0..7 && nc in 0..7

    when (piece.type) {
      PieceType.PAWN -> {
        val dir = if (piece.color == ChessColor.WHITE) -1 else 1
        val startRow = if (piece.color == ChessColor.WHITE) 6 else 1
        val oneStep = r + dir
        if (inBounds(oneStep, c) && board[oneStep][c] == null) {
          moves.add(Square(oneStep, c))
          val twoStep = r + dir * 2
          if (r == startRow && inBounds(twoStep, c) && board[twoStep][c] == null) {
            moves.add(Square(twoStep, c))
          }
        }
        for (dc in listOf(-1, 1)) {
          val nc = c + dc
          if (inBounds(oneStep, nc)) {
            val target = board[oneStep][nc]
            if (target != null && target.color != piece.color) {
              moves.add(Square(oneStep, nc))
            }
          }
        }
      }
      PieceType.KNIGHT -> {
        val deltas = listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
        for ((dr, dc) in deltas) {
          val nr = r + dr
          val nc = c + dc
          if (inBounds(nr, nc)) {
            val t = board[nr][nc]
            if (t == null || t.color != piece.color) moves.add(Square(nr, nc))
          }
        }
      }
      PieceType.BISHOP -> addSlidingMoves(board, r, c, piece.color, listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1), moves)
      PieceType.ROOK -> addSlidingMoves(board, r, c, piece.color, listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1), moves)
      PieceType.QUEEN -> addSlidingMoves(
        board, r, c, piece.color,
        listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1, -1 to 0, 1 to 0, 0 to -1, 0 to 1),
        moves
      )
      PieceType.KING -> {
        for (dr in -1..1) {
          for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val nr = r + dr
            val nc = c + dc
            if (inBounds(nr, nc)) {
              val t = board[nr][nc]
              if (t == null || t.color != piece.color) moves.add(Square(nr, nc))
            }
          }
        }
      }
    }
    return moves
  }

  private fun addSlidingMoves(
    board: List<List<ChessPiece?>>,
    r: Int,
    c: Int,
    color: ChessColor,
    directions: List<Pair<Int, Int>>,
    out: MutableList<Square>
  ) {
    for ((dr, dc) in directions) {
      var nr = r + dr
      var nc = c + dc
      while (nr in 0..7 && nc in 0..7) {
        val target = board[nr][nc]
        if (target == null) {
          out.add(Square(nr, nc))
        } else {
          if (target.color != color) out.add(Square(nr, nc))
          break
        }
        nr += dr
        nc += dc
      }
    }
  }

  private fun simulateBoard(
    board: List<List<ChessPiece?>>,
    from: Square,
    to: Square,
    piece: ChessPiece
  ): List<List<ChessPiece?>> {
    return board.mapIndexed { r, rowList ->
      rowList.mapIndexed { c, cell ->
        when {
          r == from.row && c == from.col -> null
          r == to.row && c == to.col -> piece
          else -> cell
        }
      }
    }
  }
}
