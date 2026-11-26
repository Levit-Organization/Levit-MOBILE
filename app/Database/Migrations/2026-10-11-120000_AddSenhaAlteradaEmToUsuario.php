<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

/**
 * Momento da última troca de senha. Tokens emitidos antes disso deixam de
 * valer: quem redefine a senha porque teve a conta invadida derruba as
 * sessões abertas do invasor.
 */
class AddSenhaAlteradaEmToUsuario extends Migration
{
    public function up()
    {
        $this->forge->addColumn('usuario', [
            'senha_alterada_em' => [
                'type' => 'TIMESTAMP',
                'null' => true,
            ],
        ]);
    }

    public function down()
    {
        $this->forge->dropColumn('usuario', 'senha_alterada_em');
    }
}
