<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

/**
 * Tabelas ligadas entre si: uma coluna do tipo "vinculo" guarda, em cada item,
 * o id de itens de outra tabela (o cliente de um pedido, por exemplo).
 *
 * - campo_modulo.modulo_vinculado_id diz para qual tabela a coluna aponta. A FK
 *   é NO ACTION: excluir uma tabela que outra usa é barrado pela aplicação com
 *   uma mensagem clara, e o banco segura se algo escapar. Excluir a empresa
 *   continua funcionando (as colunas somem no mesmo comando).
 * - O índice GIN em registro.dados atende às consultas por conteúdo (@>), que
 *   a pesquisa entre tabelas e a limpeza de ligações usam.
 */
class AddVinculoEntreTabelas extends Migration
{
    public function up()
    {
        $this->forge->addColumn('campo_modulo', [
            'modulo_vinculado_id' => [
                'type' => 'UUID',
                'null' => true,
            ],
        ]);
        $this->db->query('ALTER TABLE campo_modulo ADD CONSTRAINT campo_modulo_modulo_vinculado_fk FOREIGN KEY (modulo_vinculado_id) REFERENCES modulo (id)');
        $this->db->query('CREATE INDEX IF NOT EXISTS campo_modulo_vinculado_idx ON campo_modulo (modulo_vinculado_id) WHERE modulo_vinculado_id IS NOT NULL');
        $this->db->query('CREATE INDEX IF NOT EXISTS registro_dados_gin_idx ON registro USING GIN (dados jsonb_path_ops)');
    }

    public function down()
    {
        $this->db->query('DROP INDEX IF EXISTS registro_dados_gin_idx');
        $this->db->query('DROP INDEX IF EXISTS campo_modulo_vinculado_idx');
        $this->db->query('ALTER TABLE campo_modulo DROP CONSTRAINT IF EXISTS campo_modulo_modulo_vinculado_fk');
        $this->forge->dropColumn('campo_modulo', 'modulo_vinculado_id');
    }
}
