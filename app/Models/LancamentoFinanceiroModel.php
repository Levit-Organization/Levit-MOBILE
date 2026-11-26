<?php

namespace App\Models;

class LancamentoFinanceiroModel extends BaseModel
{
    protected $table      = 'lancamento_financeiro';
    protected $primaryKey = 'id';

    protected $returnType = 'array';

    protected $allowedFields = [
        'modulo_id',
        'registro_id',
        'descricao',
        'data_lancamento',
        'tipo',
        'categoria_id',
        'valor',
        'conciliado',
    ];

    protected $useTimestamps = true;
    protected $createdField  = 'criado_em';
    protected $updatedField  = 'atualizado_em';
    protected $dateFormat    = 'datetime';
}