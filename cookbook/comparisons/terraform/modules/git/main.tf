terraform {
  required_providers {
    local = {
      source = "hashicorp/local"
    }
  }
}

variable "filename" {
  type = string
}

variable "paths" {
  type = list(string)
}

resource "local_file" "ignore" {
  filename        = var.filename
  file_permission = "0644"
  content         = "# cook:begin git/ignore\n${join("\n", distinct(var.paths))}\n# cook:end git/ignore\n"
}
