<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;
use CodeIgniter\Database\RawSql;

class CreateArquivoTable extends Migration
{
    public function up()
    {
        $this->forge->addField([
            'id' => [
                'type'    => 'UUID',
                'default' => new RawSql('uuidv7()'),
            ],
            'registro_id' => [
                'type' => 'UUID',
            ],
            'nome_original' => [
                'type'       => 'VARCHAR',
                'constraint' => 255,
            ],
            'chave_armazenamento' => [
                'type'       => 'VARCHAR',
                'constraint' => 255,
                'unique'     => true,
            ],
            'tipo_mime' => [
                'type'       => 'VARCHAR',
                'constraint' => 100,
            ],
            'tamanho_bytes' => [
                'type' => 'BIGINT',
            ],
            'criado_em' => [
                'type'    => 'TIMESTAMP',
                'default' => new RawSql('CURRENT_TIMESTAMP'),
            ],
        ]);

        $this->forge->addPrimaryKey('id');
        $this->forge->addUniqueKey('registro_id');
        $this->forge->addForeignKey('registro_id', 'registro', 'id', 'CASCADE', 'CASCADE');
        $this->forge->createTable('arquivo');
    }

    public function down()
    {
        $this->forge->dropTable('arquivo');
    }
}