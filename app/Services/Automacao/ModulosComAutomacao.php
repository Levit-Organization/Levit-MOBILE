<?php

namespace App\Services\Automacao;

/**
 * Lembra (em cache) se uma tabela tem alguma automação ligada. Cada item
 * criado, editado ou excluído enfileirava um job (duas idas ao banco) mesmo
 * em tabelas sem automação nenhuma, que são a maioria. Agora só enfileira
 * quando há o que executar.
 *
 * Quem cria, edita, liga, desliga, pausa ou exclui uma automação chama
 * `esquecer()`. Uma resposta "tem" desatualizada só custa um job que não acha
 * nada para fazer; por isso o cache nunca pode ficar com "não tem" errado.
 */
final class ModulosComAutomacao
{
    private const VALIDADE = 3600;

    public static function temAtiva(string $moduloId): bool
    {
        $chave = self::chave($moduloId);
        $valor = cache($chave);

        if ($valor === null) {
            $valor = db_connect()
                ->query('SELECT EXISTS (SELECT 1 FROM automacao WHERE modulo_id = ? AND ativo) AS tem', [$moduloId])
                ->getRow()->tem;
            $valor = in_array($valor, [true, 1, '1', 't', 'true'], true) ? 1 : 0;
            cache()->save($chave, $valor, self::VALIDADE);
        }

        return $valor === 1;
    }

    public static function esquecer(string $moduloId): void
    {
        cache()->delete(self::chave($moduloId));
    }

    private static function chave(string $moduloId): string
    {
        return 'automacao_ativa_' . preg_replace('/[^A-Za-z0-9_-]/', '', $moduloId);
    }
}
