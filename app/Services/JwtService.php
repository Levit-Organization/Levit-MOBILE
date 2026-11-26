<?php

namespace App\Services;

use Firebase\JWT\JWT;
use Firebase\JWT\Key;

class JwtService
{
    protected string $secretKey;
    protected string $algorithm = 'HS256';
    protected int $ttlSeconds = 8 * 60 * 60; // 8 horas, conforme RF02

    public function __construct()
    {
        $this->secretKey = env('JWT_SECRET_KEY');
    }

    /**
     * Gera um token JWT assinado, com iat/exp adicionados automaticamente.
     */
    public function gerar(array $claims): string
    {
        helper('uuid');
        $agora = time();

        $payload = array_merge($claims, [
            'iat' => $agora,
            'exp' => $agora + $this->ttlSeconds,
            'jti' => generate_uuid_v7(),
        ]);

        return JWT::encode($payload, $this->secretKey, $this->algorithm);
    }

    /**
     * Decodifica e valida um token JWT. Lança exceção automaticamente
     * (vinda da própria biblioteca) se o token for inválido ou expirado.
     */
    public function validar(string $token): object
    {
        return JWT::decode($token, new Key($this->secretKey, $this->algorithm));
    }
}