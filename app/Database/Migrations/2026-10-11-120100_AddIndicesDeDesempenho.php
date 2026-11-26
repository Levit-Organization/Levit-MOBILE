<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

/**
 * Índices para as consultas mais frequentes e para chaves estrangeiras que
 * eram verificadas varrendo a tabela inteira (ex.: excluir um item procurava
 * no histórico de automações linha por linha). Só cria índices: nenhum dado
 * muda. IF NOT EXISTS deixa rodar de novo sem erro.
 */
class AddIndicesDeDesempenho extends Migration
{
    private const INDICES = [
        // Listagem de itens, sempre por tabela e data.
        'registro_modulo_criado_idx'        => 'registro (modulo_id, criado_em DESC)',
        'registro_criado_por_idx'           => 'registro (criado_por)',
        'registro_atualizado_por_idx'       => 'registro (atualizado_por)',
        // FK ON DELETE SET NULL: sem índice, cada exclusão de item varre o histórico.
        'automacao_log_registro_idx'        => 'automacao_log (registro_id)',
        // Login, convite e recuperação de senha procuram por LOWER(email).
        'usuario_email_lower_idx'           => 'usuario (LOWER(email))',
        'usuario_cargo_idx'                 => 'usuario (cargo_id)',
        'candidato_fase_atual_idx'          => 'candidato (fase_atual_id)',
        'convite_pendente_email_idx'        => 'convite (empresa_id, email_destinatario) WHERE aceito_em IS NULL',
        'lancamento_modulo_data_idx'        => 'lancamento_financeiro (modulo_id, data_lancamento, criado_em)',
        'automacao_campo_condicao_idx'      => 'automacao (campo_condicao_id)',
        'automacao_criado_por_idx'          => 'automacao (criado_por)',
        'notificacao_empresa_idx'           => 'notificacao (empresa_id)',
        'token_revogado_expira_idx'         => 'token_revogado (expira_em)',
    ];

    public function up()
    {
        foreach (self::INDICES as $nome => $definicao) {
            $this->db->query("CREATE INDEX IF NOT EXISTS {$nome} ON {$definicao}");
        }
    }

    public function down()
    {
        foreach (array_keys(self::INDICES) as $nome) {
            $this->db->query("DROP INDEX IF EXISTS {$nome}");
        }
    }
}
