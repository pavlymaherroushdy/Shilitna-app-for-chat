package com.example.games

enum class AuctionBudgetMode(
  val titleAr: String,
  val subtitleAr: String,
  val startingBudgetM: Int
) {
  NORMAL("مزاد عادي", "ميزانية 100M لكل لاعب", 100),
  PRO_MAX("مزاد برو ماكس", "ميزانية 200M لكل لاعب", 200)
}

enum class AuctionPhase {
  SETUP,
  BIDDING,
  LOT_RESULT,
  FINISHED
}

data class AuctionLot(
  val id: String,
  val name: String,
  val roleOrPos: String,
  val isCoach: Boolean,
  val rating: Int,
  val basePriceM: Int,
  val clubOrNation: String
)

data class WonLot(
  val lot: AuctionLot,
  val pricePaidM: Int
)

data class AuctionParticipant(
  val id: Int,
  val name: String,
  val remainingBudgetM: Int,
  val wonLots: List<WonLot> = emptyList()
)

data class AuctionState(
  val phase: AuctionPhase = AuctionPhase.SETUP,
  val budgetMode: AuctionBudgetMode = AuctionBudgetMode.NORMAL,
  val participants: List<AuctionParticipant> = emptyList(),
  val lots: List<AuctionLot> = DefaultAuctionLots,
  val currentLotIndex: Int = 0,
  val currentBidM: Int = 0,
  val highestBidderIndex: Int? = null,
  val currentTurnIndex: Int = 0,
  val passedIndices: Set<Int> = emptySet(),
  val unsoldLots: List<AuctionLot> = emptyList(),
  val lastResultMessage: String? = null,
  val lastWinnerIndex: Int? = null
) {
  val currentLot: AuctionLot?
    get() = lots.getOrNull(currentLotIndex)

  val currentTurnParticipant: AuctionParticipant?
    get() = participants.getOrNull(currentTurnIndex)

  val highestBidderParticipant: AuctionParticipant?
    get() = highestBidderIndex?.let { participants.getOrNull(it) }
}

val DefaultAuctionLots = listOf(
  AuctionLot("lot_salah", "محمد صلاح", "RW - جناح أيمن", false, 91, 15, "مصر / ليفربول"),
  AuctionLot("lot_mbappe", "كيليان مبابي", "ST - مهاجم", false, 91, 18, "فرنسا / ريال مدريد"),
  AuctionLot("lot_pep", "بيب جوارديولا", "مدرب تكتيكي", true, 94, 12, "مانشستر سيتي"),
  AuctionLot("lot_haaland", "إيرلينغ هالاند", "ST - رأس حربة", false, 91, 16, "النرويج / مان سيتي"),
  AuctionLot("lot_bellingham", "جود بيلينغهام", "CAM - صانع ألعاب", false, 90, 14, "إنجلترا / ريال مدريد"),
  AuctionLot("lot_yamal", "لامين يامال", "RW - جناح موهوب", false, 87, 12, "إسبانيا / برشلونة"),
  AuctionLot("lot_ancelotti", "كارلو أنشيلوتي", "مدرب بطولات", true, 93, 10, "ريال مدريد"),
  AuctionLot("lot_rodri", "رودري", "CDM - وسط ارتكاز", false, 91, 14, "إسبانيا / مان سيتي"),
  AuctionLot("lot_vini", "فينيسيوس جونيور", "LW - جناح أيسر", false, 90, 15, "البرازيل / ريال مدريد"),
  AuctionLot("lot_vandijk", "فيرجيل فان دايك", "CB - قلب دفاع", false, 89, 11, "هولندا / ليفربول"),
  AuctionLot("lot_courtois", "تيبو كورتوا", "GK - حارس مرمى", false, 89, 10, "بلجيكا / ريال مدريد"),
  AuctionLot("lot_marmoush", "عمر مرموش", "CF - مهاجم متعدد", false, 86, 10, "مصر / مان سيتي")
)

object AuctionEngine {

  fun startAuction(
    mode: AuctionBudgetMode,
    playerNames: List<String>,
    lots: List<AuctionLot> = DefaultAuctionLots
  ): AuctionState {
    val cleanNames = playerNames
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .ifEmpty { listOf("اللاعب 1", "اللاعب 2") }
      .let { if (it.size == 1) it + "اللاعب 2" else it }
      .take(6)

    val participants = cleanNames.mapIndexed { idx, name ->
      AuctionParticipant(
        id = idx,
        name = name,
        remainingBudgetM = mode.startingBudgetM
      )
    }

    val firstLot = lots.firstOrNull()
    return if (firstLot == null) {
      AuctionState(
        phase = AuctionPhase.FINISHED,
        budgetMode = mode,
        participants = participants,
        lots = emptyList()
      )
    } else {
      AuctionState(
        phase = AuctionPhase.BIDDING,
        budgetMode = mode,
        participants = participants,
        lots = lots,
        currentLotIndex = 0,
        currentBidM = firstLot.basePriceM,
        highestBidderIndex = null,
        currentTurnIndex = 0,
        passedIndices = emptySet()
      )
    }
  }

  fun minimumNextBid(state: AuctionState): Int {
    val lot = state.currentLot ?: return 0
    return if (state.highestBidderIndex == null) lot.basePriceM else state.currentBidM + 1
  }

  fun canCurrentPlayerBid(state: AuctionState, proposedBidM: Int): Boolean {
    if (state.phase != AuctionPhase.BIDDING) return false
    val player = state.currentTurnParticipant ?: return false
    if (state.currentTurnIndex in state.passedIndices) return false
    if (state.currentTurnIndex == state.highestBidderIndex) return false
    val minBid = minimumNextBid(state)
    return proposedBidM >= minBid && proposedBidM <= player.remainingBudgetM
  }

  fun placeBid(state: AuctionState, incrementM: Int): AuctionState {
    if (state.phase != AuctionPhase.BIDDING) return state
    val lot = state.currentLot ?: return state
    val bidderIndex = state.currentTurnIndex
    val bidder = state.participants.getOrNull(bidderIndex) ?: return state
    if (bidderIndex in state.passedIndices || bidderIndex == state.highestBidderIndex) {
      return advanceTurnOrResolve(state)
    }

    val proposedBid = if (state.highestBidderIndex == null) {
      (lot.basePriceM + incrementM).coerceAtLeast(lot.basePriceM)
    } else {
      state.currentBidM + incrementM.coerceAtLeast(1)
    }

    if (proposedBid > bidder.remainingBudgetM) return state

    val updated = state.copy(
      currentBidM = proposedBid,
      highestBidderIndex = bidderIndex
    )
    return advanceTurnOrResolve(updated)
  }

  fun passTurn(state: AuctionState): AuctionState {
    if (state.phase != AuctionPhase.BIDDING) return state
    val newPassed = state.passedIndices + state.currentTurnIndex
    val updated = state.copy(passedIndices = newPassed)
    return advanceTurnOrResolve(updated)
  }

  fun hammerCurrentLot(state: AuctionState): AuctionState {
    if (state.phase != AuctionPhase.BIDDING) return state
    val winnerIdx = state.highestBidderIndex ?: return markUnsold(state)
    return awardLot(state, winnerIdx)
  }

  fun nextLot(state: AuctionState): AuctionState {
    if (state.phase != AuctionPhase.LOT_RESULT) return state
    val nextIdx = state.currentLotIndex + 1
    if (nextIdx >= state.lots.size) {
      return state.copy(phase = AuctionPhase.FINISHED)
    }
    val nextLot = state.lots[nextIdx]
    // Pick first participant who has at least 1M or index 0
    val startIdx = state.participants.indices.firstOrNull {
      state.participants[it].remainingBudgetM >= nextLot.basePriceM
    } ?: 0
    return state.copy(
      phase = AuctionPhase.BIDDING,
      currentLotIndex = nextIdx,
      currentBidM = nextLot.basePriceM,
      highestBidderIndex = null,
      currentTurnIndex = startIdx,
      passedIndices = emptySet(),
      lastResultMessage = null,
      lastWinnerIndex = null
    )
  }

  fun finishEarly(state: AuctionState): AuctionState {
    return state.copy(phase = AuctionPhase.FINISHED)
  }

  private fun advanceTurnOrResolve(state: AuctionState): AuctionState {
    val total = state.participants.size
    if (total == 0) return state.copy(phase = AuctionPhase.FINISHED)

    val highestIdx = state.highestBidderIndex
    if (highestIdx != null) {
      // Find any other non-passed participant
      val nextIdx = findNextIndex(
        total = total,
        startAfter = state.currentTurnIndex,
        excluded = state.passedIndices + highestIdx
      )
      return if (nextIdx == null) {
        // Everyone else has passed -> award immediately to highest bidder
        awardLot(state, highestIdx)
      } else {
        state.copy(currentTurnIndex = nextIdx)
      }
    } else {
      // No bid yet: find any non-passed participant
      val nextIdx = findNextIndex(
        total = total,
        startAfter = state.currentTurnIndex,
        excluded = state.passedIndices
      )
      return if (nextIdx == null) {
        // All participants passed with no bids
        markUnsold(state)
      } else {
        state.copy(currentTurnIndex = nextIdx)
      }
    }
  }

  private fun findNextIndex(total: Int, startAfter: Int, excluded: Set<Int>): Int? {
    for (offset in 1..total) {
      val candidate = (startAfter + offset) % total
      if (candidate !in excluded) return candidate
    }
    return null
  }

  private fun awardLot(state: AuctionState, winnerIdx: Int): AuctionState {
    val lot = state.currentLot ?: return state.copy(phase = AuctionPhase.FINISHED)
    val winner = state.participants.getOrNull(winnerIdx) ?: return state
    val price = state.currentBidM.coerceAtMost(winner.remainingBudgetM)

    val updatedParticipants = state.participants.mapIndexed { idx, participant ->
      if (idx == winnerIdx) {
        participant.copy(
          remainingBudgetM = (participant.remainingBudgetM - price).coerceAtLeast(0),
          wonLots = participant.wonLots + WonLot(lot, price)
        )
      } else {
        participant
      }
    }

    return state.copy(
      phase = AuctionPhase.LOT_RESULT,
      participants = updatedParticipants,
      lastWinnerIndex = winnerIdx,
      lastResultMessage = "تم البيع! ${lot.name} ينضم إلى ${winner.name} مقابل ${price}M"
    )
  }

  private fun markUnsold(state: AuctionState): AuctionState {
    val lot = state.currentLot ?: return state.copy(phase = AuctionPhase.FINISHED)
    return state.copy(
      phase = AuctionPhase.LOT_RESULT,
      unsoldLots = state.unsoldLots + lot,
      lastWinnerIndex = null,
      lastResultMessage = "لم يزايد أحد — لم يتم بيع ${lot.name}"
    )
  }
}
