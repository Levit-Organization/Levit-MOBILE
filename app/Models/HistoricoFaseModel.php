<?php

namespace App\Models;

class HistoricoFaseModel extends BaseModel
{
    protected $table      = 'historico_fase';
    protected $primaryKey = 'id';

    protected $returnType = 'array';

    protected $allowedFields = [
        'candidato_id',
        'fase_anterior_id',
        'fase_nova_id',
        'alterado_por',
    ];

    protected $useTimestamps = false;
}