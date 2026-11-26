package com.android.purebilibili.feature.settings

import androidx.annotation.DrawableRes
import com.android.purebilibili.R

internal enum class DonationPaymentChannel {
    WECHAT,
    ALIPAY,
}

/** 展示、展开过渡与保存共用当前所选渠道对应的赞赏码原图，默认优先微信。 */
@DrawableRes
internal fun donationQrResource(channel: DonationPaymentChannel = DonationPaymentChannel.WECHAT): Int =
    when (channel) {
        DonationPaymentChannel.WECHAT -> R.drawable.author_qr_wechat
        DonationPaymentChannel.ALIPAY -> R.drawable.author_qr_alipay
    }
