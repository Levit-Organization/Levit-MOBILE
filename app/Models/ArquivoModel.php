<?php

namespace App\Models;

class ArquivoModel extends BaseModel
{
    protected $table      = 'arquivo';
    protected $primaryKey = 'id';

    protected $returnType = 'array';

    protected $allowedFields = [
        'registro_id',
        'nome_original',
        'chave_armazenamento',
        'tipo_mime',
        'tamanho_bytes',
    ];

    protected $useTimestamps = false;
}