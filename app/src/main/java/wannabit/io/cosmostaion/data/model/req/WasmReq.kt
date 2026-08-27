package wannabit.io.cosmostaion.data.model.req

class ICNSInfoReq(icns: String?) {
    private var address_by_icns: AddressByIcns

    init {
        address_by_icns = AddressByIcns(icns)
    }

    inner class AddressByIcns(var icns: String?)
}

class NSStargazeInfoReq(name: String?) {
    var associated_address: AssociatedAddress

    init {
        associated_address = AssociatedAddress(name)
    }

    inner class AssociatedAddress(var name: String?)
}

data class NSArchwayReq(val resolve_record: ResolveRecord?)
data class ResolveRecord(val name: String?)

data class StarCw721TokenIdReq(val tokens: Token)
data class Token(val owner: String?, val limit: Int = 50, val start_after: String = "0")

data class StarCw721TokenInfoReq(val nft_info: NftInfo)
data class NftInfo(val token_id: String)

data class SimulateSwapOperationsReq(val simulate_swap_operations: SimulateSwapOperations)
data class SimulateSwapOperations(
    val offer_amount: String,
    val operations: List<SwapOperation>
)

data class SwapOperation(
    val astroport: AstroportOperation? = null,
    val pool: PoolOperation? = null
)

data class PoolOperation(
    val pool: Pool
)

data class Pool(
    val offer_asset: OfferAsset
)

data class OfferAsset(
    val info: AssetInfo,
    val amount: String
)

data class AssetInfo(
    val native_token: NativeToken
)

data class NativeToken(
    val denom: String
)

data class SimulationReq(val simulation: Simulation)
data class Simulation(val offer_asset: OfferAsset)

data class AstroportOperation(
    val native_swap: NativeSwap
)

data class NativeSwap(
    val offer_denom: String,
    val ask_denom: String
)
