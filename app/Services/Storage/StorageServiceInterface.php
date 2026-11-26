<?php

namespace App\Services\Storage;

interface StorageServiceInterface
{
    /**
     * Salva um arquivo e devolve a chave de armazenamento gerada
     * (nunca o nome original do arquivo).
     */
    public function salvar(string $caminhoTemporario, string $extensao): string;

    /**
     * Devolve o conteúdo binário de um arquivo salvo anteriormente.
     */
    public function obter(string $chaveArmazenamento): string;

    /**
     * Remove um arquivo do armazenamento remoto.
     */
    public function excluir(string $chaveArmazenamento): void;
}