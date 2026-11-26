<?php

namespace App\Models;

class FaseRecrutamentoModel extends BaseModel
{
    protected $table      = 'fase_recrutamento';
    protected $primaryKey = 'id';

    protected $returnType = 'array';

    protected $allowedFields = [
        'modulo_id',
        'nome',
        'ordem',
    ];

    protected $useTimestamps = false;
}