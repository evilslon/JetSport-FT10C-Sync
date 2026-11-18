package com.jetsport.ft10csync.data

import android.content.Context

/** Owns the user's calculation/bracelet profile preferences. Existing preference
 * file and keys are intentionally preserved so upgrades keep saved values. */
class UserProfileRepository(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("jetsport_step_profile", Context.MODE_PRIVATE)

    data class Values(
        val heightCm: Int,
        val weightKg: Int,
        val ageYears: Int,
        val isMale: Boolean
    )

    fun load(): Values = Values(
        heightCm = preferences.getInt(KEY_HEIGHT_CM, DEFAULT_HEIGHT_CM),
        weightKg = preferences.getInt(KEY_WEIGHT_KG, DEFAULT_WEIGHT_KG),
        ageYears = preferences.getInt(KEY_AGE_YEARS, DEFAULT_AGE_YEARS),
        isMale = preferences.getBoolean(KEY_IS_MALE, DEFAULT_IS_MALE)
    )

    fun save(heightCm: Int, weightKg: Int, ageYears: Int, isMale: Boolean) {
        preferences.edit()
            .putInt(KEY_HEIGHT_CM, heightCm)
            .putInt(KEY_WEIGHT_KG, weightKg)
            .putInt(KEY_AGE_YEARS, ageYears)
            .putBoolean(KEY_IS_MALE, isMale)
            .apply()
    }

    private companion object {
        const val KEY_HEIGHT_CM = "height_cm"
        const val KEY_WEIGHT_KG = "weight_kg"
        const val KEY_AGE_YEARS = "age_years"
        const val KEY_IS_MALE = "is_male"
        const val DEFAULT_HEIGHT_CM = 187
        const val DEFAULT_WEIGHT_KG = 120
        const val DEFAULT_AGE_YEARS = 45
        const val DEFAULT_IS_MALE = true
    }
}
