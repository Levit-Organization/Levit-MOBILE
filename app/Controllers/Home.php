<?php

namespace App\Controllers;

use CodeIgniter\HTTP\ResponseInterface;
class Home extends BaseApiController
{
    public function index(): string
    {
        return view('welcome_message');
    }

    public function naoEncontrado(): ResponseInterface
    {
        return $this->respondError('Recurso não encontrado.', 404);
    }
}
