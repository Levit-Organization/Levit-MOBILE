<?php

if (! function_exists('generate_uuid_v7')) {
    /**
     * Gera um UUID versão 7 (RFC 9562): ordenado no tempo, mas ainda assim
     * praticamente impossível de advinhar. Mesmo formato do uuidv7() nativo
     * do PostgreSQL, que usamos como DEFAULT nas migrations.
     */
    function generate_uuid_v7(): string
    {
        // 48 bits com o timestamp atual em milissegundos, big-endian
        $unixTsMs  = (int) (microtime(true) * 1000);
        $timeBytes = substr(pack('J', $unixTsMs), 2, 6);

        // 80 bits aleatórios (usaremos 74: 12 bits de rand_a + 62 de rand_b)
        $randBytes = random_bytes(10);

        // Byte 6: marca a versão "0111" (7) nos 4 bits mais significativos
        $randBytes[0] = chr((ord($randBytes[0]) & 0x0f) | 0x70);

        // Byte 8: marca a variante "10" nos 2 bits mais significativos
        $randBytes[2] = chr((ord($randBytes[2]) & 0x3f) | 0x80);

        $hex = bin2hex($timeBytes . $randBytes);

        return sprintf(
            '%s-%s-%s-%s-%s',
            substr($hex, 0, 8),
            substr($hex, 8, 4),
            substr($hex, 12, 4),
            substr($hex, 16, 4),
            substr($hex, 20, 12)
        );
    }
}