terraform {
  required_providers {

    helm = {
      source  = "hashicorp/helm"
      version = "3.1.1"
    }

    aws = {
      source  = "hashicorp/aws"
      version = "6.32.1"
    }
  }

  backend "s3" {
    bucket = "nicholasmeyers-api-skoda-prd-terraform-state"
    key    = "terraform.tfstate"
    region = "eu-west-1"
  }
}

provider "helm" {
  kubernetes = {
    config_path = "~/.kube/config"
  }
}
