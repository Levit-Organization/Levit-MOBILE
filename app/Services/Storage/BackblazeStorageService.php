<?php

namespace App\Services\Storage;

use Aws\S3\S3Client;
use Aws\S3\Exception\S3Exception;

class BackblazeStorageService implements StorageServiceInterface
{
    private S3Client $client;
    private string $bucket;

    private const VARIAVEIS = ['B2_ENDPOINT', 'B2_REGION', 'B2_BUCKET', 'B2_KEY_ID', 'B2_APPLICATION_KEY'];

    public function __construct()
    {
        $faltando = array_filter(self::VARIAVEIS, static fn (string $nome): bool => trim((string) env($nome)) === '');

        if ($faltando !== []) {
            log_message('error', '[Storage] Variáveis ausentes no .env: ' . implode(', ', $faltando));

            throw new \RuntimeException('O armazenamento de arquivos não está configurado no servidor. Avise o administrador.');
        }

        $this->bucket = env('B2_BUCKET');

        $this->client = new S3Client([
            'version'                 => 'latest',
            'region'                  => env('B2_REGION'),
            'endpoint'                => 'https://' . env('B2_ENDPOINT'),
            'use_path_style_endpoint' => true,
            'credentials'             => [
                'key'    => env('B2_KEY_ID'),
                'secret' => env('B2_APPLICATION_KEY'),
            ],
        ]);
    }

    public function salvar(string $caminhoTemporario, string $extensao): string
    {
        helper('uuid');
        $chave = generate_uuid_v7() . '.' . $extensao;

        try {
            $this->client->putObject([
                'Bucket'     => $this->bucket,
                'Key'        => $chave,
                'SourceFile' => $caminhoTemporario,
            ]);
        } catch (S3Exception $e) {
            throw new \RuntimeException('Não foi possível salvar o arquivo no armazenamento remoto.');
        }

        return $chave;
    }

    public function obter(string $chaveArmazenamento): string
    {
        try {
            $resultado = $this->client->getObject([
                'Bucket' => $this->bucket,
                'Key'    => $chaveArmazenamento,
            ]);
        } catch (S3Exception $e) {
            throw new \RuntimeException('Não foi possível obter o arquivo do armazenamento remoto.');
        }

        return (string) $resultado['Body'];
    }

    public function excluir(string $chaveArmazenamento): void
    {
        try {
            $this->client->deleteObject([
                'Bucket' => $this->bucket,
                'Key'    => $chaveArmazenamento,
            ]);
        } catch (S3Exception $e) {
            throw new \RuntimeException('Não foi possível excluir o arquivo do armazenamento remoto.');
        }
    }
}