<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;
use CodeIgniter\Database\RawSql;

/**
 * Histórico do que foi feito na plataforma e por quem. É a base dos
 * relatórios em PDF. Guarda o nome da pessoa e da tabela no momento da ação:
 * o histórico continua legível mesmo se a pessoa sair ou a tabela for excluída.
 */
class CreateAtividadeTable extends Migration
{
    public function up()
    {
        $this->forge->addField([
            'id' => [
                'type'    => 'UUID',
                'default' => new RawSql('uuidv7()'),
            ],
            'empresa_id' => [
                'type' => 'UUID',
            ],
            // Nulo quando quem fez foi o sistema (automação, candidatura pública).
            'usuario_id' => [
                'type' => 'UUID',
                'null' => true,
            ],
            'usuario_nome' => [
                'type'       => 'VARCHAR',
                'constraint' => 100,
                'null'       => true,
            ],
            // item_criado, item_alterado, tabela_criada, membro_convidado...
            'acao' => [
                'type'       => 'VARCHAR',
                'constraint' => 40,
            ],
            'modulo_id' => [
                'type' => 'UUID',
                'null' => true,
            ],
            'modulo_nome' => [
                'type'       => 'VARCHAR',
                'constraint' => 100,
                'null'       => true,
            ],
            'descricao' => [
                'type' => 'TEXT',
            ],
            'criado_em' => [
                'type'    => 'TIMESTAMP',
                'default' => new RawSql('CURRENT_TIMESTAMP'),
            ],
        ]);

        $this->forge->addPrimaryKey('id');
        $this->forge->addForeignKey('empresa_id', 'empresa', 'id', 'CASCADE', 'CASCADE');
        // Sem FK em usuario_id/modulo_id de propósito: o histórico sobrevive à exclusão.
        $this->forge->createTable('atividade');

        $this->db->query('CREATE INDEX atividade_empresa_criado_idx ON atividade (empresa_id, criado_em DESC)');
    }

    public function down()
    {
        $this->forge->dropTable('atividade');
    }
}
