<?php

namespace App\Models;

class ConviteModel extends BaseModel
{
    protected $table      = 'convite';
    protected $primaryKey = 'id';

    protected $returnType = 'array';

    protected $allowedFields = [
        'empresa_id',
        'convidado_por',
        'cargo_id',
        'email_destinatario',
        'token_hash',
        'expira_em',
        'aceito_em',
        'mensagem',
    ];

    protected $useTimestamps = false;
}