package com.example.levit.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/** Mesmos endpoints de backupService.js do web (exportar). */
interface BackupApi {

    @GET("backup/json")
    suspend fun exportarJson(): Response<ResponseBody>

    @GET("modulos/{id}/exportar-csv")
    suspend fun exportarCsv(@Path("id") moduloId: String): Response<ResponseBody>
}
