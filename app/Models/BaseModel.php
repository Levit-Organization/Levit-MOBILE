<?php

namespace App\Models;

use CodeIgniter\Model;

class BaseModel extends Model
{
    protected $useAutoIncrement = false;

    protected $beforeInsert = ['generateUuid'];

    /**
     * Gera um UUID v7 para a chave primária antes do INSERT,
     * caso ela ainda não tenha sido informada manualmente.
     */
    protected function generateUuid(array $data): array
    {
        if (empty($data['data'][$this->primaryKey])) {
            helper('uuid');
            $data['data'][$this->primaryKey] = generate_uuid_v7();
        }

        return $data;
    }
}