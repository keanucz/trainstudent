package com.trainstudent.ui.offers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainstudent.network.Offer
import com.trainstudent.repository.TrainStudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OffersUiState(
    val offers: List<Offer> = emptyList(),
    val availableOperators: List<String> = emptyList(),
    val availableDiscountTypes: List<String> = emptyList(),
    val selectedOperator: String? = null,
    val selectedDiscountType: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class OffersViewModel @Inject constructor(
    private val repository: TrainStudentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OffersUiState())
    val uiState: StateFlow<OffersUiState> = _uiState.asStateFlow()

    private var allOffers: List<Offer> = emptyList()

    init {
        loadOffers()
    }

    private fun loadOffers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Use hardcoded offers based on research from chat1.md, chat2.md, chat3.md
            allOffers = HARDCODED_OFFERS
            _uiState.update {
                it.copy(
                    offers = allOffers,
                    availableOperators = allOffers.map { o -> o.operator }.distinct().sorted(),
                    availableDiscountTypes = allOffers.map { o -> o.discountType }.distinct().sorted(),
                    isLoading = false
                )
            }
        }
    }

    fun setOperatorFilter(operator: String?) {
        _uiState.update { it.copy(selectedOperator = operator) }
        applyFilters()
    }

    fun setDiscountTypeFilter(discountType: String?) {
        _uiState.update { it.copy(selectedDiscountType = discountType) }
        applyFilters()
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(
                selectedOperator = null,
                selectedDiscountType = null,
                offers = allOffers
            )
        }
    }

    private fun applyFilters() {
        val operator = _uiState.value.selectedOperator
        val discountType = _uiState.value.selectedDiscountType

        val filtered = allOffers.filter { offer ->
            val matchesOperator = operator == null || offer.operator == operator
            val matchesType = discountType == null || offer.discountType == discountType
            matchesOperator && matchesType
        }

        _uiState.update { it.copy(offers = filtered) }
    }

    companion object {
        /**
         * Hardcoded offers based on research from chat1.md, chat2.md, chat3.md
         * These are verified student discount portals and stacking strategies
         */
        val HARDCODED_OFFERS = listOf(
            // ============== BEST VALUE: Stackable Promo Codes ==============
            Offer(
                id = "tpe-student",
                operator = "TransPennine Express",
                title = "TPESTUDENT - 50% Off Advance Tickets",
                description = "Use code TPESTUDENT at checkout for 50% off single Advance tickets. STACKS with 16-25 Railcard for maximum savings!",
                discountPercent = 50,
                discountType = "Promo Code",
                requiredRailcard = "16-25 Railcard",
                validFrom = "2024-01-01",
                validTo = "2026-12-31",
                link = "https://www.tpexpress.co.uk/special-offers/student-train-tickets"
            ),
            
            Offer(
                id = "northern-50",
                operator = "Northern",
                title = "Northern 50% Code (App Exclusive)",
                description = "Get a unique 50% off code valid on the Northern app. Sign up with your email to receive a personal code. Works on ALL Northern services!",
                discountPercent = 50,
                discountType = "App Code",
                requiredRailcard = null,
                validFrom = "2024-01-01",
                validTo = "2026-12-31",
                link = "https://help.northernrailway.co.uk/s/appcode"
            ),

            // ============== UNiDAYS Verified (25% off) ==============
            Offer(
                id = "avanti-unidays",
                operator = "Avanti West Coast",
                title = "UNiDAYS - 25% Off Advance Tickets",
                description = "Verify via UNiDAYS for 25% off Advance tickets. Often beats Railcard prices on busy routes like London-Manchester!",
                discountPercent = 25,
                discountType = "UNiDAYS",
                requiredRailcard = null,
                validFrom = "2024-01-01",
                validTo = "2026-12-31",
                link = "https://www.myunidays.com/GB/en-GB/partners/avantiwest/access/online"
            ),
            
            Offer(
                id = "heathrow-express-unidays",
                operator = "Heathrow Express",
                title = "UNiDAYS - 25% Off Express Saver",
                description = "25% off Express Saver fares via UNiDAYS. Applies to all Express Saver fares without peak restrictions.",
                discountPercent = 25,
                discountType = "UNiDAYS",
                requiredRailcard = null,
                validFrom = "2024-01-01",
                validTo = "2026-12-31",
                link = "https://www.myunidays.com/GB/en-GB/partners/heathrowexpress/access/online"
            ),
            
            Offer(
                id = "lner-unidays",
                operator = "LNER",
                title = "Student Advance (Periodic)",
                description = "Occasionally releases 'Student Advance' fares via UNiDAYS for 25-30% off Edinburgh-London routes. Check at term start!",
                discountPercent = 27,
                discountType = "UNiDAYS",
                requiredRailcard = null,
                validFrom = "2024-01-01",
                validTo = "2026-12-31",
                link = "https://www.lner.co.uk/offers/"
            ),

            // ============== Student Beans Verified ==============
            Offer(
                id = "hull-trains-studentbeans",
                operator = "Hull Trains",
                title = "Student Beans - 20% Off",
                description = "Periodic 20% off Advance tickets via Student Beans. Look for 'student sale' banners!",
                discountPercent = 20,
                discountType = "Student Beans",
                requiredRailcard = null,
                validFrom = "2024-01-01",
                validTo = "2026-12-31",
                link = "https://www.studentbeans.com/student-discount/uk/hull-trains"
            ),
            
            Offer(
                id = "emr-studentbeans",
                operator = "East Midlands Railway",
                title = "Student Beans Advance Fares",
                description = "Limited student-specific Advance fares via Student Beans verification on Leeds/Nottingham-London routes.",
                discountPercent = 15,
                discountType = "Student Beans",
                requiredRailcard = null,
                validFrom = "2024-01-01",
                validTo = "2026-12-31",
                link = "https://www.studentbeans.com/student-discount/uk/east-midlands-railway"
            ),

            // ============== Direct Student Verification ==============
            Offer(
                id = "grand-central-student",
                operator = "Grand Central",
                title = "Student Discount (No Railcard Needed!)",
                description = "Grand Central doesn't accept Railcards, but offers ~10% off Advance fares with student verification (.ac.uk email or TOTUM).",
                discountPercent = 10,
                discountType = "Student Email",
                requiredRailcard = null,
                validFrom = "2024-01-01",
                validTo = "2026-12-31",
                link = "https://www.grandcentralrail.com/student-discount"
            ),
            
            Offer(
                id = "xc-student",
                operator = "CrossCountry",
                title = "Student Advance Fares",
                description = "Intermittent 'Student Advance' fares requiring TOTUM verification. Look for 'Student' label next to Advance fares.",
                discountPercent = 15,
                discountType = "TOTUM",
                requiredRailcard = null,
                validFrom = "2024-01-01",
                validTo = "2026-12-31",
                link = "https://www.crosscountrytrains.co.uk/tickets-savings/offers-discounts/student-train-travel"
            ),

            // ============== Seatfrog Secret Fares ==============
            Offer(
                id = "seatfrog-secretfare",
                operator = "Multiple Operators",
                title = "Seatfrog Secretfare - Up to 80% Off",
                description = "Secret discounted fares on East Midlands Railway, LNER, CrossCountry and more. Prices from £5!",
                discountPercent = 60,
                discountType = "Secretfare",
                requiredRailcard = null,
                validFrom = "2024-01-01",
                validTo = "2026-12-31",
                link = "https://secretfare.seatfrog.com/"
            ),

            // ============== Cashback Stacking ==============
            Offer(
                id = "tcb-cashback",
                operator = "All Operators",
                title = "TopCashback - Up to 3.5% Back",
                description = "Earn cashback on train tickets through TopCashback. Works with most train booking sites!",
                discountPercent = 3,
                discountType = "Cashback",
                requiredRailcard = null,
                validFrom = "2024-01-01",
                validTo = "2026-12-31",
                link = "https://www.topcashback.co.uk/search/?q=train"
            ),
            
            Offer(
                id = "quidco-cashback",
                operator = "All Operators",
                title = "Quidco - Up to 3% Back",
                description = "Earn cashback on train tickets through Quidco. Book through their links for extra savings!",
                discountPercent = 3,
                discountType = "Cashback",
                requiredRailcard = null,
                validFrom = "2024-01-01",
                validTo = "2026-12-31",
                link = "https://www.quidco.com/merchant/trainline/"
            ),

            // ============== Free Railcard Offers ==============
            Offer(
                id = "santander-railcard",
                operator = "Santander",
                title = "FREE 4-Year 16-25 Railcard",
                description = "Open a Santander Edge Student Account and get a FREE 4-year 16-25 Railcard worth £120! Plus earn Quidco cashback on signup.",
                discountPercent = 33,
                discountType = "Free Railcard",
                requiredRailcard = null,
                validFrom = "2024-01-01",
                validTo = "2026-12-31",
                link = "https://www.quidco.com/merchant/santander-edge-student-current-account/"
            ),

            // ============== Standard Railcard (for reference) ==============
            Offer(
                id = "railcard-1625",
                operator = "All Operators",
                title = "16-25 Railcard - 1/3 Off",
                description = "Standard 1/3 off most train fares. £30/year or free with Santander Edge. Stacks with TPESTUDENT!",
                discountPercent = 33,
                discountType = "Railcard",
                requiredRailcard = "16-25 Railcard",
                validFrom = "2024-01-01",
                validTo = "2026-12-31",
                link = "https://www.16-25railcard.co.uk/"
            )
        )
    }
}
