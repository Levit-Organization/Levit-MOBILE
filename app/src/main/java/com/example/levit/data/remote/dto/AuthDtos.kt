package com.example.levit.data.remote.dto

/**
 * Corpo enviado para POST /auth/login
 */
data class LoginRequest(
    val email: String,
    val senha: String
)

/**
 * Corpo enviado para POST /auth/registrar
 * O backend exige nome_empresa; quando o usuário não informa uma
 * empresa separada, repetimos o próprio nome (mesma regra do LEVIT Web).
 */
data class RegistrarRequest(
    val nome: String,
    val email: String,
    val senha: String,
    val cnpj_cpf: String,
    val nome_empresa: String
)

data class Usuario(
    val id: String,
    val nome: String,
    val email: String
)

data class Empresa(
    val id: String,
    val nome: String
)

/**
 * Payload de sucesso de login/registrar: { token, usuario, empresa }
 */
data class AuthData(
    val token: String,
    val usuario: Usuario,
    val empresa: Empresa
)

/**
 * Envelope padrão da API: { "status": "success", "data": {...} }
 * ou { "status": "error", "message": "...", "errors": {...} }
 * O mesmo formato é usado por toda a API CodeIgniter (BaseApiController).
 */
data class ApiEnvelope<T>(
    val status: String? = null,
    val data: T? = null,
    val message: String? = null,
    val errors: Map<String, Any>? = null
)