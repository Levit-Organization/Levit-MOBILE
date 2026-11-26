<?php

namespace App\Models;

class NotificacaoModel extends BaseModel
{
    protected $table      = 'notificacao';
    protected $primaryKey = 'id';

    protected $returnType = 'array';

    protected $allowedFields = [
        'empresa_id',
        'usuario_id',
        'tipo',
        'titulo',
        'mensagem',
        'link',
        'agrupador',
        'contador',
        'lida_em',
        'criado_em',
    ];

    protected array $casts = [
        'contador' => 'int',
    ];

    protected $useTimestamps = false;
}
