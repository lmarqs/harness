terraform {
  required_version = ">= 1.5.0"
  required_providers {
    local = {
      source  = "hashicorp/local"
      version = "2.9.1"
    }
  }
}

variable "git_enabled" {
  type    = bool
  default = true
}

module "npm" {
  source = "./modules/npm"
}

module "git" {
  source = "./modules/git"
  count  = var.git_enabled ? 1 : 0

  filename = "${path.root}/output/.gitignore"
  paths    = module.npm.ephemeral_paths
}
