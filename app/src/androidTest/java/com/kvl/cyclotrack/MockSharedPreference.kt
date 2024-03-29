package com.kvl.cyclotrack

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.annotation.Nullable

/**
 * Mock implementation of shared preference, which just saves data in memory using map.
 */
class MockSharedPreference : SharedPreferences {
    private val preferenceMap: HashMap<String, Any?> = HashMap()
    private val preferenceEditor: MockSharedPreferenceEditor =
        MockSharedPreferenceEditor(preferenceMap)

    override fun getAll(): Map<String, *> {
        return preferenceMap
    }

    @Nullable
    override fun getString(s: String, @Nullable s1: String?): String? {
        return preferenceMap[s] as String?
    }

    @Nullable
    override fun getStringSet(s: String, @Nullable set: Set<String>?): Set<String>? {
        if (!preferenceMap.containsKey(s)) return set
        return preferenceMap[s] as Set<String>?
    }

    override fun getInt(s: String, i: Int): Int {
        if (!preferenceMap.containsKey(s)) return i
        return preferenceMap[s] as Int
    }

    override fun getLong(s: String, l: Long): Long {
        if (!preferenceMap.containsKey(s)) return l
        return preferenceMap[s] as Long
    }

    override fun getFloat(s: String, v: Float): Float {
        if (!preferenceMap.containsKey(s)) return v
        return preferenceMap[s] as Float
    }

    override fun getBoolean(s: String, b: Boolean): Boolean {
        if (!preferenceMap.containsKey(s)) return b
        return preferenceMap[s] as Boolean
    }

    override fun contains(s: String): Boolean {
        return preferenceMap.containsKey(s)
    }

    override fun edit(): SharedPreferences.Editor {
        return preferenceEditor
    }

    override fun registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener: OnSharedPreferenceChangeListener) {}
    override fun unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener: OnSharedPreferenceChangeListener) {}
    class MockSharedPreferenceEditor(private val preferenceMap: HashMap<String, Any?>) :
        SharedPreferences.Editor {
        override fun putString(s: String, @Nullable s1: String?): SharedPreferences.Editor {
            preferenceMap[s] = s1
            return this
        }

        override fun putStringSet(
            s: String,
            @Nullable set: Set<String>?,
        ): SharedPreferences.Editor {
            preferenceMap[s] = set
            return this
        }

        override fun putInt(s: String, i: Int): SharedPreferences.Editor {
            preferenceMap[s] = i
            return this
        }

        override fun putLong(s: String, l: Long): SharedPreferences.Editor {
            preferenceMap[s] = l
            return this
        }

        override fun putFloat(s: String, v: Float): SharedPreferences.Editor {
            preferenceMap[s] = v
            return this
        }

        override fun putBoolean(s: String, b: Boolean): SharedPreferences.Editor {
            preferenceMap[s] = b
            return this
        }

        override fun remove(s: String): SharedPreferences.Editor {
            preferenceMap.remove(s)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            preferenceMap.clear()
            return this
        }

        override fun commit(): Boolean {
            return true
        }

        override fun apply() {
            // Nothing to do, everything is saved in memory.
        }
    }

}