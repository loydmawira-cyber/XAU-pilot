from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, Protocol
import uuid


@dataclass
class ProvisionRequest:
    installation_id: str
    region: str
    instance_type: str
    ami_id: str | None = None
    subnet_id: str | None = None
    security_group_id: str | None = None
    iam_instance_profile: str | None = None
    dry_run: bool = True


@dataclass
class ProviderHost:
    provider: str
    provider_instance_id: str
    region: str
    status: str
    metadata: Dict[str, Any]


class HostingProvider(Protocol):
    name: str

    def provision_host(self, request: ProvisionRequest) -> ProviderHost:
        ...

    def get_host(self, provider_instance_id: str, region: str) -> ProviderHost:
        ...

    def stop_host(self, provider_instance_id: str, region: str) -> None:
        ...

    def terminate_host(self, provider_instance_id: str, region: str) -> None:
        ...


class AwsEc2Provider:
    name = "aws_ec2"

    def __init__(self, boto_client_factory=None):
        self._boto_client_factory = boto_client_factory

    def _client(self, region: str):
        if self._boto_client_factory:
            return self._boto_client_factory(region)
        try:
            import boto3
        except ImportError as exc:
            raise RuntimeError("boto3 is required when HOSTING_PROVIDER=aws_ec2") from exc
        return boto3.client("ec2", region_name=region)

    def provision_host(self, request: ProvisionRequest) -> ProviderHost:
        if request.dry_run:
            return ProviderHost(
                provider=self.name,
                provider_instance_id=f"dryrun-{uuid.uuid4().hex[:12]}",
                region=request.region,
                status="SIMULATED",
                metadata={"instance_type": request.instance_type, "dry_run": True},
            )
        if not request.ami_id or not request.subnet_id or not request.security_group_id:
            raise ValueError("AWS AMI, subnet, and security group are required for provisioning")

        client = self._client(request.region)
        params = {
            "ImageId": request.ami_id,
            "InstanceType": request.instance_type,
            "MinCount": 1,
            "MaxCount": 1,
            "SubnetId": request.subnet_id,
            "SecurityGroupIds": [request.security_group_id],
            "TagSpecifications": [{
                "ResourceType": "instance",
                "Tags": [
                    {"Key": "Name", "Value": f"xaupilot-{request.installation_id}"},
                    {"Key": "XauPilotInstallation", "Value": request.installation_id},
                ],
            }],
        }
        if request.iam_instance_profile:
            params["IamInstanceProfile"] = {"Name": request.iam_instance_profile}
        response = client.run_instances(**params)
        instance = response["Instances"][0]
        return ProviderHost(
            provider=self.name,
            provider_instance_id=instance["InstanceId"],
            region=request.region,
            status="HOST_ALLOCATING",
            metadata={"instance_type": request.instance_type, "raw_state": instance.get("State", {})},
        )

    def get_host(self, provider_instance_id: str, region: str) -> ProviderHost:
        response = self._client(region).describe_instances(InstanceIds=[provider_instance_id])
        reservations = response.get("Reservations", [])
        if not reservations or not reservations[0].get("Instances"):
            raise RuntimeError("AWS instance not found")
        instance = reservations[0]["Instances"][0]
        state = instance.get("State", {}).get("Name", "unknown")
        return ProviderHost(self.name, provider_instance_id, region, state.upper(), {"raw_state": instance.get("State", {})})

    def stop_host(self, provider_instance_id: str, region: str) -> None:
        self._client(region).stop_instances(InstanceIds=[provider_instance_id])

    def terminate_host(self, provider_instance_id: str, region: str) -> None:
        self._client(region).terminate_instances(InstanceIds=[provider_instance_id])
