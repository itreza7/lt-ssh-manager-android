package com.larateam.sshmanager.data.db

import androidx.room.TypeConverter
import com.larateam.sshmanager.data.model.AuthMethod

/** Room type converters. [AuthMethod] is stored as its enum name. */
class Converters {
    @TypeConverter
    fun authMethodToString(value: AuthMethod): String = value.name

    @TypeConverter
    fun stringToAuthMethod(value: String): AuthMethod = AuthMethod.fromName(value)
}
