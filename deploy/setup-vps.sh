#!/usr/bin/env bash
# Prepara una VM de Oracle Cloud (Ubuntu 22.04/24.04, Ampere A1 arm64 o AMD x86_64).
# Ejecútalo una vez con el usuario por defecto (ubuntu):  bash deploy/setup-vps.sh
# Es idempotente: se puede repetir sin efectos secundarios.
set -euo pipefail

if [[ ! -r /etc/os-release ]] || ! grep -q '^ID=ubuntu' /etc/os-release; then
  echo "Este script es para Ubuntu. Para Oracle Linux mira el README (firewalld)." >&2
  exit 1
fi

echo "==> Actualizando el sistema"
sudo apt-get update -y
sudo DEBIAN_FRONTEND=noninteractive apt-get upgrade -y
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y ca-certificates curl git make unattended-upgrades

echo "==> Actualizaciones automáticas de seguridad"
sudo dpkg-reconfigure -f noninteractive unattended-upgrades

echo "==> Abriendo 80/tcp, 443/tcp y 443/udp en el firewall de la instancia"
# Las imágenes de Ubuntu de OCI traen reglas iptables que solo dejan pasar SSH. Oracle desaconseja
# ufw (sus reglas propias protegen el acceso al volumen de arranque), así que se añaden reglas
# antes del REJECT final y se persisten en /etc/iptables/rules.v4 sin volcar las de Docker. Una regla
# añadida después del REJECT no sirve (gana la primera que coincide): si está ahí, se vuelve a poner delante.
RULES=/etc/iptables/rules.v4
open_port() {
  local proto=$1 port=$2
  local rule=(-p "$proto" -m state --state NEW -m "$proto" --dport "$port" -j ACCEPT)
  local line="-A INPUT ${rule[*]}"
  local rule_pos reject_pos
  # "|| true": grep sin coincidencias no debe parar el script (set -e con pipefail).
  rule_pos=$(sudo iptables -S INPUT | grep -nxF -- "$line" | head -1 | cut -d: -f1 || true)
  reject_pos=$(sudo iptables -S INPUT | grep -n -- '^-A INPUT -j REJECT' | head -1 | cut -d: -f1 || true)
  if [[ -n "$rule_pos" && -n "$reject_pos" && "$rule_pos" -gt "$reject_pos" ]]; then
    sudo iptables -D INPUT "${rule[@]}"
    rule_pos=""
  fi
  if [[ -z "$rule_pos" ]]; then
    local reject_line
    reject_line=$(sudo iptables -L INPUT --line-numbers -n | awk '$2 == "REJECT" {print $1; exit}')
    if [[ -n "$reject_line" ]]; then
      sudo iptables -I INPUT "$reject_line" "${rule[@]}"
    else
      sudo iptables -A INPUT "${rule[@]}"
    fi
  fi
  if [[ -f "$RULES" ]]; then
    # En el fichero que se carga al arrancar: fuera la línea esté donde esté, y de nuevo antes del REJECT.
    { sudo grep -vxF -- "$line" "$RULES" || true; } | sudo tee "$RULES.tmp" >/dev/null
    sudo mv "$RULES.tmp" "$RULES"
    sudo sed -i "0,/^-A INPUT -j REJECT/s//${line}\n&/" "$RULES"
  fi
}
open_port tcp 80
open_port tcp 443
open_port udp 443

echo "==> Endureciendo SSH (solo clave, sin root)"
sudo tee /etc/ssh/sshd_config.d/99-control-horario.conf >/dev/null <<'EOF'
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin no
EOF
sudo systemctl reload ssh || sudo systemctl reload sshd

echo "==> Instalando Docker Engine y el plugin Compose (repositorio oficial)"
if ! command -v docker >/dev/null; then
  sudo install -m 0755 -d /etc/apt/keyrings
  sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  sudo chmod a+r /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
  sudo apt-get update -y
  sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"

# La forma AMD gratuita (E2.1.Micro) solo tiene 1 GB de RAM: añade 2 GB de swap.
if [[ $(awk '/MemTotal/ {print $2}' /proc/meminfo) -lt 2000000 ]] && ! swapon --show | grep -q /swapfile; then
  echo "==> Poca memoria: creando 2 GB de swap"
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

cat <<'EOF'

Listo. Cierra la sesión SSH y vuelve a entrar para usar docker sin sudo. Después sigue docs/DEPLOY.md:
  1. cd ~/control-horario && cp .env.example .env && chmod 600 .env   (rellena los valores de prod)
  2. make deploy
EOF
