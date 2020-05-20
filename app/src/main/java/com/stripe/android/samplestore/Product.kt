package com.stripe.android.samplestore

internal enum class Product(
    internal val emoji: String,
    internal val price: Int
) {
    Shirt("👕", 2000),
    Pants("👖", 4000),
    Dress("👗", 3000),
    MansShoe("👞", 700),
    AthleticShoe("👟", 2000),
    HighHeeledShoe("👠", 1000),
    WomansSandal("👡", 2000),
    WomansBoots("👢", 2500),
    WomansHat("👒", 800),
    Bikini("👙", 3000),
    Lipstick("💄", 2000),
    TopHat("🎩", 5000),
    Purse("👛", 5500),
    Handbag("👜", 6000),
    Sunglasses("🕶", 2000),
    WomansClothes("👚", 2500)
}
