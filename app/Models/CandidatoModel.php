<?php

namespace App\Models;

class CandidatoModel extends BaseModel
{
    protected $table      = 'candidato';
    protected $primaryKey = 'id';

    protected $returnType = 'array';

    protected $allowedFields = [
        'modulo_id',
        'fase_atual_id',
        'nome',
        'email',
        'telefone',
        'mensagem',
        'curriculo_chave',
        'curriculo_nome',
        'curriculo_mime',
        'curriculo_tamanho',
    ];

    protected $useTimestamps = false;
}