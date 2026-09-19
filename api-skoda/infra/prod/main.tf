module "application" {
  source           = "git::https://github.com/nicholasM95/terraform-modules.git//modules/k8s-helm-release?ref=v1.15.5"
  image_tag        = var.image_tag
  application_name = "api-skoda"
  namespace_name   = "skoda"
  helm_path        = "../../application"
  docker_config    = var.docker_config
}


