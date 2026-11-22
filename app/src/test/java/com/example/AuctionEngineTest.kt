package com.example

import com.example.games.AuctionBudgetMode
import com.example.games.AuctionEngine
import com.example.games.AuctionLot
import com.example.games.AuctionPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuctionEngineTest {

  private val sampleLots = listOf(
    AuctionLot("lot_1", "محمد صلاح", "RW", false, 91, 15, "مصر / ليفربول"),
    AuctionLot("lot_2", "بيب جوارديولا", "مدرب", true, 94, 12, "مان سيتي")
  )

  @Test
  fun `normal and pro max budget modes initialize exact starting budgets`() {
    val normalState = AuctionEngine.startAuction(
      mode = AuctionBudgetMode.NORMAL,
      playerNames = listOf("أحمد", "كريم"),
      lots = sampleLots
    )
    assertEquals(AuctionPhase.BIDDING, normalState.phase)
    assertEquals(listOf(100, 100), normalState.participants.map { it.remainingBudgetM })

    val proMaxState = AuctionEngine.startAuction(
      mode = AuctionBudgetMode.PRO_MAX,
      playerNames = listOf("أحمد", "كريم", "عمر"),
      lots = sampleLots
    )
    assertEquals(AuctionPhase.BIDDING, proMaxState.phase)
    assertEquals(listOf(200, 200, 200), proMaxState.participants.map { it.remainingBudgetM })
  }

  @Test
  fun `pass progression awards lot when all other players pass without getting stuck`() {
    var state = AuctionEngine.startAuction(
      mode = AuctionBudgetMode.NORMAL,
      playerNames = listOf("أحمد", "كريم", "عمر"),
      lots = sampleLots
    )

    // Turn 0 (أحمد) bids base price 15M -> turn advances cleanly to Turn 1 (كريم)
    state = AuctionEngine.placeBid(state, incrementM = 0)
    assertEquals(AuctionPhase.BIDDING, state.phase)
    assertEquals(15, state.currentBidM)
    assertEquals(0, state.highestBidderIndex)
    assertEquals(1, state.currentTurnIndex)

    // Turn 1 (كريم) raises by +5M -> 20M -> turn advances to Turn 2 (عمر)
    state = AuctionEngine.placeBid(state, incrementM = 5)
    assertEquals(20, state.currentBidM)
    assertEquals(1, state.highestBidderIndex)
    assertEquals(2, state.currentTurnIndex)

    // Turn 2 (عمر) passes -> turn goes back to Turn 0 (أحمد)
    state = AuctionEngine.passTurn(state)
    assertEquals(AuctionPhase.BIDDING, state.phase)
    assertEquals(0, state.currentTurnIndex)

    // Turn 0 (أحمد) passes -> all others passed, lot awards immediately to كريم for 20M
    state = AuctionEngine.passTurn(state)
    assertEquals(AuctionPhase.LOT_RESULT, state.phase)
    assertEquals(1, state.lastWinnerIndex)
    assertEquals(80, state.participants[1].remainingBudgetM)
    assertEquals(1, state.participants[1].wonLots.size)
    assertEquals("lot_1", state.participants[1].wonLots.first().lot.id)
    assertEquals(20, state.participants[1].wonLots.first().pricePaidM)

    // Advance to next lot (Pep Guardiola coach lot)
    state = AuctionEngine.nextLot(state)
    assertEquals(AuctionPhase.BIDDING, state.phase)
    assertEquals(1, state.currentLotIndex)
    assertNull(state.highestBidderIndex)
    assertTrue(state.passedIndices.isEmpty())
  }

  @Test
  fun `all players passing with zero bids marks lot unsold cleanly and proceeds`() {
    var state = AuctionEngine.startAuction(
      mode = AuctionBudgetMode.PRO_MAX,
      playerNames = listOf("أحمد", "كريم"),
      lots = sampleLots
    )

    state = AuctionEngine.passTurn(state)
    assertEquals(AuctionPhase.BIDDING, state.phase)
    assertEquals(1, state.currentTurnIndex)

    state = AuctionEngine.passTurn(state)
    assertEquals(AuctionPhase.LOT_RESULT, state.phase)
    assertNull(state.lastWinnerIndex)
    assertEquals(1, state.unsoldLots.size)
    assertEquals(listOf(200, 200), state.participants.map { it.remainingBudgetM })

    state = AuctionEngine.nextLot(state)
    assertEquals(AuctionPhase.BIDDING, state.phase)
    assertEquals(1, state.currentLotIndex)
  }
}
