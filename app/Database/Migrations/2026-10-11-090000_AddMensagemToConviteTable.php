<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

/** Mensagem opcional de quem convida, mostrada no e-mail e na página do convite. */
class AddMensagemToConviteTable extends Migration
{
    public function up()
    {
        $this->forge->addColumn('convite', [
            'mensagem' => [
                'type' => 'TEXT',
                'null' => true,
            ],
        ]);
    }

    public function down()
    {
        $this->forge->dropColumn('convite', 'mensagem');
    }
}
