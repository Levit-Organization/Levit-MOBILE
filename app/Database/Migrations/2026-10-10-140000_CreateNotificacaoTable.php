<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;
use CodeIgniter\Database\RawSql;

class CreateNotificacaoTable extends Migration
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
            'usuario_id' => [
                'type' => 'UUID',
            ],
            // candidatura, automacao, automacao_pausada, convite_aceito...
            'tipo' => [
                'type'       => 'VARCHAR',
                'constraint' => 30,
            ],
            'titulo' => [
                'type'       => 'VARCHAR',
                'constraint' => 200,
            ],
            'mensagem' => [
                'type' => 'TEXT',
                'null' => true,
            ],
            // Rota do frontend para onde a notificação leva (ex.: /recrutamento).
            'link' => [
                'type'       => 'VARCHAR',
                'constraint' => 255,
                'null'       => true,
            ],
            // Notificações não lidas com o mesmo agrupador viram uma só ("3 novas candidaturas").
            'agrupador' => [
                'type'       => 'VARCHAR',
                'constraint' => 120,
                'null'       => true,
            ],
            'contador' => [
                'type'    => 'INTEGER',
                'default' => 1,
            ],
            'lida_em' => [
                'type' => 'TIMESTAMP',
                'null' => true,
            ],
            'criado_em' => [
                'type'    => 'TIMESTAMP',
                'default' => new RawSql('CURRENT_TIMESTAMP'),
            ],
        ]);

        $this->forge->addPrimaryKey('id');
        $this->forge->addForeignKey('empresa_id', 'empresa', 'id', 'CASCADE', 'CASCADE');
        $this->forge->addForeignKey('usuario_id', 'usuario', 'id', 'CASCADE', 'CASCADE');
        $this->forge->createTable('notificacao');

        // Lista do usuário (mais recentes primeiro) e contador de não lidas, que é
        // consultado o tempo todo pelo sino: índice parcial só com as não lidas.
        $this->db->query('CREATE INDEX notificacao_usuario_criado_idx ON notificacao (usuario_id, criado_em DESC)');
        $this->db->query('CREATE INDEX notificacao_nao_lidas_idx ON notificacao (usuario_id, agrupador) WHERE lida_em IS NULL');
    }

    public function down()
    {
        $this->forge->dropTable('notificacao');
    }
}
