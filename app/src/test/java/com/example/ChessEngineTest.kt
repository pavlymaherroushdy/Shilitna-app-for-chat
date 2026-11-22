package com.example

import com.example.games.ChessColor
import com.example.games.ChessEngine
import com.example.games.ChessGameState
import com.example.games.ChessPiece
import com.example.games.PieceType
import com.example.games.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessEngineTest {

  @Test
  fun `initial white pawn move and bot response update turn and move count`() {
    var state = ChessGameState()
    // Tap white e2 pawn (row 6, col 4)
    state = ChessEngine.onSquareTapped(state, Square(6, 4))
    assertEquals(Square(6, 4), state.selected)
    assertTrue(Square(5, 4) in state.legalTargets)
    assertTrue(Square(4, 4) in state.legalTargets)

    // Move e2 -> e4
    state = ChessEngine.onSquareTapped(state, Square(4, 4))
    assertEquals(ChessColor.BLACK, state.turn)
    assertEquals(1, state.moveCount)
    assertNotNull(state.board[4][4])
    assertEquals(PieceType.PAWN, state.board[4][4]?.type)

    // Bot picks a legal black move
    val botMove = ChessEngine.pickBotMove(state)
    assertNotNull(botMove)
    state = ChessEngine.applyMove(state, botMove!!.first, botMove.second)
    assertEquals(ChessColor.WHITE, state.turn)
    assertEquals(2, state.moveCount)
  }

  @Test
  fun `fools mate detects checkmate winner cleanly`() {
    var state = ChessGameState()
    // 1. f2-f3
    state = ChessEngine.applyMove(state, Square(6, 5), Square(5, 5))
    // 1... e7-e5
    state = ChessEngine.applyMove(state, Square(1, 4), Square(3, 4))
    // 2. g2-g4
    state = ChessEngine.applyMove(state, Square(6, 6), Square(4, 6))
    // 2... Qd8-h4#
    state = ChessEngine.applyMove(state, Square(0, 3), Square(4, 7))

    assertEquals(ChessColor.BLACK, state.winner)
    assertEquals(ChessColor.WHITE, state.inCheck)
  }

  @Test
  fun `undo restores previous board and turn`() {
    var state = ChessGameState()
    state = ChessEngine.applyMove(state, Square(6, 4), Square(4, 4))
    assertEquals(ChessColor.BLACK, state.turn)
    state = ChessEngine.undoMove(state)
    assertEquals(ChessColor.WHITE, state.turn)
    assertEquals(0, state.moveCount)
    assertEquals(ChessPiece(PieceType.PAWN, ChessColor.WHITE), state.board[6][4])
  }
}
