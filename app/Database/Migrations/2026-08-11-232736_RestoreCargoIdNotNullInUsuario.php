<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

class RestoreCargoIdNotNullInUsuario extends Migration
{
    public function up()
    {
        $this->db->query('ALTER TABLE usuario ALTER COLUMN cargo_id SET NOT NULL');
    }

    public function down()
    {
        $this->db->query('ALTER TABLE usuario ALTER COLUMN cargo_id DROP NOT NULL');
    }
}