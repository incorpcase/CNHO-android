package wannabit.io.cosmostaion.data.api

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface KeybaseApi {
    @GET("_/api/1.0/user/lookup.json")
    suspend fun getValidatorInfo(@Query("key_suffix") keySuffix: String): Response<JsonObject>
}
