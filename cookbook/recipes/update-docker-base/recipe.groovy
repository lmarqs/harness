@param(['dockerfile'], default = 'Dockerfile') String dockerfile
@param(['stage'], doc = 'The named build stage') String stage
@param(['expected'], doc = 'The image the stage uses now') String expected
@param(['image'], doc = 'The image to use instead') String image

@ingredient(Docker) def docker
@ingredient(Mise) def mise

docker.dockerfile = dockerfile
docker.baseImage(stage, image, expected)
mise.task('docker:verify', docker.buildCommand(stage))
