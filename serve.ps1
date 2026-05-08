param(
  [int]$StartPort = 8080
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

function Get-ContentType([string]$path) {
  switch ([System.IO.Path]::GetExtension($path).ToLowerInvariant()) {
    ".html" { "text/html; charset=utf-8" }
    ".css" { "text/css; charset=utf-8" }
    ".js" { "application/javascript; charset=utf-8" }
    ".md" { "text/plain; charset=utf-8" }
    default { "application/octet-stream" }
  }
}

function Get-NetworkUrls([int]$port) {
  $addresses = [System.Net.Dns]::GetHostAddresses([System.Net.Dns]::GetHostName())
  $privateIpv4 = @()

  foreach ($address in $addresses) {
    if ($address.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork) {
      continue
    }

    $ip = $address.ToString()

    if ($ip.StartsWith("10.") -or $ip.StartsWith("192.168.") -or ($ip -match "^172\.(1[6-9]|2[0-9]|3[0-1])\.")) {
      $privateIpv4 += "http://$ip`:$port/"
    }
  }

  return $privateIpv4
}

function Write-Response([System.IO.Stream]$stream, [int]$statusCode, [string]$contentType, [byte[]]$body) {
  $reason = if ($statusCode -eq 200) { "OK" } else { "Not Found" }
  $header = "HTTP/1.1 $statusCode $reason`r`nContent-Type: $contentType`r`nContent-Length: $($body.Length)`r`nConnection: close`r`n`r`n"
  $headerBytes = [System.Text.Encoding]::ASCII.GetBytes($header)
  $stream.Write($headerBytes, 0, $headerBytes.Length)
  $stream.Write($body, 0, $body.Length)
}

$port = $StartPort
$listener = $null
$bindAddress = [System.Net.IPAddress]::Any

while ($port -lt ($StartPort + 100)) {
  try {
    $listener = [System.Net.Sockets.TcpListener]::new($bindAddress, $port)
    $listener.Start()
    break
  } catch {
    if ($listener) {
      $listener.Stop()
      $listener = $null
    }
    $port += 1
  }
}

if (-not $listener) {
  throw "Could not start local server on ports $StartPort through $($StartPort + 99)."
}

$prefix = "http://localhost:$port/"
$networkUrls = Get-NetworkUrls $port
$prefix | Set-Content -Path (Join-Path $root ".server-url") -Encoding ASCII
Write-Host "Serving $root at $prefix"
if ($networkUrls.Count -gt 0) {
  Write-Host "Phone URLs on this network:"
  foreach ($url in $networkUrls) {
    Write-Host "  $url"
  }
} else {
  Write-Host "No private network IPv4 address was found. Run ipconfig and use your Wi-Fi IPv4 address if needed."
}

try {
  while ($true) {
    $client = $null

    try {
      $client = $listener.AcceptTcpClient()
      $stream = $client.GetStream()
      $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::ASCII, $false, 1024, $true)
      $requestLine = $reader.ReadLine()

      while ($reader.Peek() -ge 0) {
        $line = $reader.ReadLine()
        if ([string]::IsNullOrEmpty($line)) {
          break
        }
      }

      if (-not $requestLine) {
        $client.Close()
        continue
      }

      $parts = $requestLine.Split(" ")
      $requestPath = "/"

      if ($parts.Length -ge 2) {
        $requestPath = $parts[1].Split("?")[0]
      }

      $requestPath = [Uri]::UnescapeDataString($requestPath.TrimStart("/"))

      if ([string]::IsNullOrWhiteSpace($requestPath)) {
        $requestPath = "index.html"
      }

      $fullPath = Join-Path $root $requestPath
      $resolvedPath = [System.IO.Path]::GetFullPath($fullPath)
      $resolvedRoot = [System.IO.Path]::GetFullPath($root)

      if (-not $resolvedPath.StartsWith($resolvedRoot, [System.StringComparison]::OrdinalIgnoreCase) -or -not (Test-Path -LiteralPath $resolvedPath -PathType Leaf)) {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes("Not found")
        Write-Response $stream 404 "text/plain; charset=utf-8" $bytes
        $client.Close()
        continue
      }

      $bytes = [System.IO.File]::ReadAllBytes($resolvedPath)
      Write-Response $stream 200 (Get-ContentType $resolvedPath) $bytes
      $client.Close()
    } catch [System.Management.Automation.PipelineStoppedException] {
      throw
    } catch {
      if ($client) {
        $client.Close()
      }
      Write-Warning $_.Exception.Message
    }
  }
} finally {
  Write-Host "Stopping server."
  $listener.Stop()
}
