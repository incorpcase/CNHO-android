package wannabit.io.cosmostaion.ui.tx.genTx

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class TargetAsset(
    val image: String? = null,
    val symbol: String? = null,
    val denom: String,
    val type: TargetAssetType? = null,
    val description: String? = null,
    val decimals: Int? = 6
) : Parcelable
