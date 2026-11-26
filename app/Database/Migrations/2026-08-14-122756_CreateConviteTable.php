<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;
use CodeIgniter\Database\RawSql;

class CreateConviteTable extends Migration
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
            'convidado_por' => [
                'type' => 'UUID',
                'null' => true,
            ],
            'cargo_id' => [
                'type' => 'UUID',
            ],
            'email_destinatario' => [
                'type'       => 'VARCHAR',
                'constraint' => 150,
            ],
            'token_hash' => [
                'type'       => 'VARCHAR',
                'constraint' => 64,
                'unique'     => true,
            ],
            'expira_em' => [
                'type' => 'TIMESTAMP',
            ],
            'aceito_em' => [
                'type' => 'TIMESTAMP',
                'null' => true,
            ],
            'criado_em' => [
                'type'    => 'TIMESTAMP',
                'default' => new RawSql('CURRENT_TIMESTAMP'),
            ],
        ]);

        $this->forge->addPrimaryKey('id');
        $this->forge->addKey('empresa_id');
        $this->forge->addForeignKey('empresa_id', 'empresa', 'id', 'CASCADE', 'CASCADE');
        $this->forge->addForeignKey('convidado_por', 'usuario', 'id', 'CASCADE', 'SET NULL');
        $this->forge->addForeignKey('cargo_id', 'cargo', 'id', 'CASCADE', 'CASCADE');
        $this->forge->createTable('convite');
    }

    public function down()
    {
        $this->forge->dropTable('convite');
    }
}